package dotty.tools.dotc.transform

import dotty.meta.TastyString
import dotty.tools.dotc.ast.tpd
import dotty.tools.dotc.core.Constants._
import dotty.tools.dotc.core.Contexts._
import dotty.tools.dotc.core.Types._
import dotty.tools.dotc.core.Symbols._
import dotty.tools.dotc.transform.MegaPhase.MiniPhase
import dotty.tools.dotc.core.tasty.Quotes._

/** TODO
 */
class SerializeQuotes extends MiniPhase {
  import tpd._

  override def phaseName: String = "serializeQuotes"

  override def transformApply(tree: tpd.Apply)(implicit ctx: Context): tpd.Tree = tree.fun match {
    case fun: TypeApply if fun.symbol eq defn.MetaQuote => serializeQuote(tree.args.head, fun.args.head.tpe)
    case _ => tree
  }

  private def serializeQuote(tree: tpd.Tree, tpe: Type)(implicit ctx: Context): tpd.Tree = {
    val tastyBytes = pickle(encapsulateQuote(tree))
    val tastyString = Literal(Constant(TastyString.tastyToString(tastyBytes)))
    val exprTpe = defn.MetaTastyExpr.typeRef.appliedTo(tpe)
    tpd.New(exprTpe, tastyString :: ref(defn.NilModuleRef) :: Nil)
  }
}
