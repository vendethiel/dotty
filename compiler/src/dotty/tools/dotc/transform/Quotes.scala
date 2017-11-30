package dotty.tools.dotc.transform

import dotty.tools.dotc.ast.tpd
import dotty.tools.dotc.core.Constants._
import dotty.tools.dotc.core.Contexts._
import dotty.tools.dotc.core.Definitions._
import dotty.tools.dotc.core.Decorators._
import dotty.tools.dotc.core.Types._
import dotty.tools.dotc.core.Symbols._
import dotty.tools.dotc.core.tasty.{PositionPickler, TastyPickler, TastyPrinter}
import dotty.tools.dotc.config.Printers._
import dotty.tools.dotc.transform.MegaPhase.MiniPhase

import scala.collection.mutable.ListBuffer

/** TODO
 */
class Quotes extends MiniPhase {
  import tpd._

  override def phaseName: String = "quotes"

  override def transformApply(tree: tpd.Apply)(implicit ctx: Context): tpd.Tree = {
    tree.fun match {
      case fun: TypeApply if fun.symbol eq defn.QuoteApply =>
        val tastyString = Literal(Constant(new String(pickle(tree.args.head))))
        val exprTpe = defn.QuoteExpr.typeRef.appliedTo(fun.args.head.tpe)
        tpd.New(exprTpe, tastyString :: Nil)
      case _ => tree
    }
  }

  private def pickle(tree: tpd.Tree)(implicit ctx: Context): Array[Byte] = {
    val pickler = new TastyPickler()
    val treePkl = pickler.treePkl
    treePkl.pickle(tree :: Nil)
    treePkl.compactify()
    pickler.addrOfTree = treePkl.buf.addrOfTree
    pickler.addrOfSym = treePkl.addrOfSym
    if (tree.pos.exists)
      new PositionPickler(pickler, treePkl.buf.addrOfTree).picklePositions(tree :: Nil)

    // other pickle sections go here.
    val pickled = pickler.assembleParts()

    if (pickling ne noPrinter) {
      println(i"**** pickled quote of \n${tree.show}")
      new TastyPrinter(pickled).printContents()
    }

    pickled
  }
}
