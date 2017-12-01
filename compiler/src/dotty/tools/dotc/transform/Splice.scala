package dotty.tools.dotc.transform

import java.net.URLClassLoader

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

  override def phaseName: String = "splice"

  override def transformApply(tree: tpd.Apply)(implicit ctx: Context): tpd.Tree = {
    tree.fun match {
      case fun: TypeApply if fun.symbol eq defn.MetaSplice =>
        def splice(str: String): Tree = {
          val splicedCode = unpickle(new dotty.meta.Expr(str).tasty)
          transformAllDeep(splicedCode)
        }
        tree.args.head match {
          // splice explicit quotes
          case Apply(TypeApply(_, _), List(Literal(Constant(tastyStr: String)))) => splice(tastyStr)
          case Inlined(_, _, Apply(TypeApply(_, _), List(Literal(Constant(tastyStr: String))))) => splice(tastyStr)

          // splice quotes with code ~'x --> x
          case Apply(quote, arg :: Nil) if quote.symbol == defn.MetaQuote => arg
          case Inlined(_, _, Apply(quote, arg :: Nil)) if quote.symbol == defn.MetaQuote => arg

          case _ => tree
        }
      case _ => tree
    }
  }


  override def transformSelect(tree: tpd.Select)(implicit ctx: Context) = transformRefTree(tree)

  override def transformIdent(tree: tpd.Ident)(implicit ctx: Context) = transformRefTree(tree)

  private def transformRefTree(tree: RefTree)(implicit ctx: Context): Tree = {
    if (tree.tpe.derivesFrom(defn.MetaExpr)) reflectQuote(tree.symbol).getOrElse(tree)
    else tree
  }

  private def reflectQuote(sym: Symbol)(implicit ctx: Context): Option[Tree] = {
    val urls = ctx.settings.classpath.value.split(':').map(cp => java.nio.file.Paths.get(cp).toUri.toURL)
    val classLoader = new URLClassLoader(urls, getClass.getClassLoader)
    val clazz = classLoader.loadClass(sym.owner.showFullName)
    val method = clazz.getDeclaredMethod(sym.name.toString)
    val expr = method.invoke(null).asInstanceOf[dotty.meta.Expr[_]]
    println(expr.tastyString)
    val code = unpickle(expr.tasty)
    Some(ref(defn.MetaQuote).appliedToType(code.tpe).appliedTo(code))
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
    case PackageDef(_, (vdef @ DefDef(_, _, _, _, _)) :: Nil) => vdef.rhs
  }
}
