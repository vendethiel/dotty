package dotty.tools.dotc.transform

import java.nio.charset.StandardCharsets

import dotty.meta.TastyString
import dotty.tools.dotc.ast.tpd
import dotty.tools.dotc.ast.Trees._
import dotty.tools.dotc.core.Constants._
import dotty.tools.dotc.core.Contexts._
import dotty.tools.dotc.core.Decorators._
import dotty.tools.dotc.core.Names._
import dotty.tools.dotc.core.Types._
import dotty.tools.dotc.core.Symbols._
import dotty.tools.dotc.core.tasty.{DottyUnpickler, PositionPickler, TastyPickler, TastyPrinter}
import dotty.tools.dotc.config.Printers._
import dotty.tools.dotc.transform.MegaPhase.MiniPhase

/** TODO
 */
class Quote extends MiniPhase {
  import tpd._

  override def phaseName: String = "quote"

  override def transformApply(tree: tpd.Apply)(implicit ctx: Context): tpd.Tree = {
    tree.fun match {
      case fun: TypeApply if fun.symbol eq defn.MetaQuote => quote(tree.args.head, fun.args.head.tpe)
      case _ => tree
    }
  }

  private def quote(tree: tpd.Tree, tpe: Type)(implicit ctx: Context): tpd.Tree = {
    val tastyBytes = pickle(tree)
    val tastyString = Literal(Constant(TastyString.tastyToString(tastyBytes)))
    val exprTpe = defn.MetaExpr.typeRef.appliedTo(tpe)
    tpd.New(exprTpe, tastyString :: Nil)
  }

  private def pickle(tree0: tpd.Tree)(implicit ctx: Context): Array[Byte] = {
    val tree = encapsulateQuote(tree0)
    val pickler = new TastyPickler()
    val treePkl = pickler.treePkl
    treePkl.pickle(tree :: Nil)
    treePkl.compactify()
    pickler.addrOfTree = treePkl.buf.addrOfTree
    pickler.addrOfSym = treePkl.addrOfSym
    // if (tree.pos.exists)
    //   new PositionPickler(pickler, treePkl.buf.addrOfTree).picklePositions(tree :: Nil)

    // other pickle sections go here.
    val pickled = pickler.assembleParts()

    if (pickling ne noPrinter) {
      println(i"**** pickled quote of \n${tree0.show}")
      new TastyPrinter(pickled).printContents()
    }

    pickled
  }

  private def encapsulateQuote(tree: tpd.Tree)(implicit ctx: Context): tpd.Tree = {
    val quoteName = "quotedCode1".toTermName
    val quotePackage = defn.RootPackage
    val quotedVal = tpd.SyntheticValDef(quoteName, tree)(ctx.withOwner(quotePackage))
    tpd.PackageDef(tpd.ref(quotePackage).asInstanceOf[tpd.Ident], quotedVal :: Nil)
  }

}
