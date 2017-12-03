package dotty.tools.dotc.core.tasty

import dotty.tools.dotc.ast.tpd
import dotty.tools.dotc.ast.Trees._
import dotty.tools.dotc.core.Contexts._
import dotty.tools.dotc.core.Decorators._
import dotty.tools.dotc.core.Flags._
import dotty.tools.dotc.core.Types._
import dotty.tools.dotc.core.Symbols._
import dotty.tools.dotc.config.Printers._

object Quotes {
  import tpd._

  def pickle(tree: tpd.Tree)(implicit ctx: Context): Array[Byte] = {
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
      println(i"**** pickled quote of \n${tree.show}")
      new TastyPrinter(pickled).printContents()
    }

    pickled
  }

  def unpickle(bytes: Array[Byte])(implicit ctx: Context): tpd.Tree = {
    val unpickler = new DottyUnpickler(bytes)
    unpickler.enter(roots = Set(defn.RootPackage))
    val tree = unpickler.body.head
    if (pickling ne noPrinter) {
      println(i"**** unpickled quote for \n${tree.show}")
      new TastyPrinter(bytes).printContents()
    }
    tree
  }

  // Quote TASTY encapsulation

  def encapsulateQuote(tree: tpd.Tree)(implicit ctx: Context): tpd.Tree = {
    val quoteName = "'".toTermName
    val quotePackage = defn.RootPackage
    val methodType = MethodType(Nil)(_ => Nil, _ => tree.tpe)
    val sym = ctx.newSymbol(quotePackage, quoteName, Method, methodType).asTerm
    val quotedDef = tpd.DefDef(sym, params => tree)(ctx.withOwner(quotePackage))
    tpd.PackageDef(tpd.ref(quotePackage).asInstanceOf[tpd.Ident], quotedDef :: Nil)
  }

  def revealQuote(tree: tpd.Tree)(implicit ctx: Context): tpd.Tree = tree match {
    case PackageDef(_, (vdef @ DefDef(_, _, _, _, _)) :: Nil) => vdef.rhs
  }

}
