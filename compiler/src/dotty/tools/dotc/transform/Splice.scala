package dotty.tools.dotc.transform

import dotty.tools.dotc.ast.Trees._
import dotty.tools.dotc.ast.tpd
import dotty.tools.dotc.config.Printers._
import dotty.tools.dotc.core.Constants._
import dotty.tools.dotc.core.Contexts._
import dotty.tools.dotc.core.Decorators._
import dotty.tools.dotc.core.Symbols._
import dotty.tools.dotc.core.Types._
import dotty.tools.dotc.core.tasty.{DottyUnpickler, TastyPickler, TastyPrinter}
import dotty.tools.dotc.transform.MegaPhase.MiniPhase

/** TODO
 */
class Splice extends MiniPhase {
  import tpd._

  override def phaseName: String = "quotes"

  override def transformApply(tree: tpd.Apply)(implicit ctx: Context): tpd.Tree = {
    tree.fun match {
      case fun: TypeApply if fun.symbol eq defn.SpliceApply =>
        tree.args.head match {
          case Apply(TypeApply(_, _), List(Literal(Constant(str: String)))) => // TODO add guards checking that this is a Quote.Expr constructor
            val splicedCode = unpickle(stringToBytes(str))
            transformAllDeep(splicedCode)
          case _ =>
            tree
        }
      case _ => tree
    }
  }

  private def unpickle(bytes: Array[Byte])(implicit ctx: Context): tpd.Tree = {
    val unpickler = new DottyUnpickler(bytes)
    unpickler.enter(roots = Set(defn.RootPackage))
    val quote = revealQuote(unpickler.body.head)
    if (pickling ne noPrinter) {
      println(i"**** unpickled quote for \n${quote.show}")
      new TastyPrinter(bytes).printContents()
    }
    quote
  }

  private def revealQuote(tree: tpd.Tree)(implicit ctx: Context): tpd.Tree = tree match {
    case PackageDef(_, (vdef @ ValDef(_, _, _)) :: Nil) => vdef.rhs
  }

  // TODO improve string representation
  private def stringToBytes(str: String): Array[Byte] = str.split(',').iterator.map(java.lang.Byte.parseByte).toArray

}
