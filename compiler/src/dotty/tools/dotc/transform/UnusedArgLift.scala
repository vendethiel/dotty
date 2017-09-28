package dotty.tools.dotc.transform

import dotty.tools.dotc.ast.tpd
import dotty.tools.dotc.core.Contexts._
import dotty.tools.dotc.core.Definitions._
import dotty.tools.dotc.core.Symbols._
import dotty.tools.dotc.core.Types._
import dotty.tools.dotc.transform.TreeTransforms.{MiniPhaseTransform, TransformerInfo}
import dotty.tools.dotc.typer.EtaExpansion

import scala.collection.mutable.ListBuffer

/** This phase extracts the unused arguments before the application to avoid losing any
 *  effects in the argument tree. This trivializes the removal of parameter in the Erasure phase.
 *
 *  `f(x1,...)(y1,...)...(...)` with at least one unused argument
 *
 *    -->
 *
 *  `val ev$f = f` // if `f` is some expression that needs evaluation
 *  `val ev$x1 = x1`
  *  ...
 *  `val ev$y1 = y1`
  *  ...
 *  `ev$f(ev$x1,...)(ev$y1,...)...(...)`
 *
 */
class UnusedArgLift extends MiniPhaseTransform {
  import tpd._

  override def phaseName: String = "unusedArgLift"

  /** Check what the phase achieves, to be called at any point after it is finished. */
  override def checkPostCondition(tree: Tree)(implicit ctx: Context): Unit = tree match {
    case tree: Apply =>
      tree.args.foreach { arg =>
        assert(!arg.tpe.isUnused || isPureExpr(arg))
      }
    case _ =>
  }

  /* Tree transform */

  override def transformApply(tree: Apply)(implicit ctx: Context, info: TransformerInfo): Tree = tree.tpe.widen match {
    case _: MethodType => tree // Do the transformation higher in the tree if needed
    case _ =>
      if (!hasUnusedArgs(tree)) tree
      else {
        val buffer = ListBuffer.empty[Tree]
        val app = EtaExpansion.liftApp(buffer, tree)
        if (buffer.isEmpty) app
        else Block(buffer.result(), app)
      }
  }

  /* private methods */

  /** Returns true if at least on of the arguments is an impure unused.
   *  Inner applies are also checked in case of multiple parameter list.
   */
  private def hasUnusedArgs(tree: Apply)(implicit ctx: Context): Boolean = {
    def hasUnusedArgs(tpe: Type): Boolean = tpe match {
      case tpe: MethodType =>
        tpe.paramInfos.exists(_.isUnused) || // TODO check if the unused argument is pure
        hasUnusedArgs(tpe.resType)
      case tpe: MethodicType =>
        hasUnusedArgs(tpe.resultType)
      case _ => false
    }
    hasUnusedArgs(tree.fun.symbol.info)
  }


}
