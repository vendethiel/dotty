package dotty.tools.dotc.transform

import dotty.tools.dotc.ast.Trees._
import dotty.tools.dotc.ast.tpd
import dotty.tools.dotc.core.Constants._
import dotty.tools.dotc.core.Contexts._
import dotty.tools.dotc.core.Decorators._
import dotty.tools.dotc.core.Symbols._
import dotty.tools.dotc.transform.MegaPhase.MiniPhase
import dotty.tools.dotc.core.tasty.Quotes._

import java.net.URLClassLoader

import dotty.meta._

/** TODO
 */
class Splice extends MiniPhase {
  import tpd._

  override def phaseName: String = "splice"

  override def transformApply(tree: tpd.Apply)(implicit ctx: Context): tpd.Tree = {
    tree.fun match {
      case fun: TypeApply if fun.symbol eq defn.MetaSplice =>
        def splice(str: String): Tree = {
          val splicedCode = revealQuote(unpickle(new TastyExpr(str, Nil).tasty)) // TODO add args
          transformAllDeep(splicedCode)
        }
        tree.args.head match {
          // splice explicit quotes
          case Apply(TypeApply(_, _), List(Literal(Constant(tastyStr: String)))) => splice(tastyStr)
          case Inlined(_, _, Apply(TypeApply(_, _), List(Literal(Constant(tastyStr: String))))) => splice(tastyStr)

          // splice quotes with code ~'x --> x
          case Apply(quote, arg :: Nil) if quote.symbol == defn.MetaQuote => arg
          case Inlined(_, _, Apply(quote, arg :: Nil)) if quote.symbol == defn.MetaQuote => arg

          case quote: Apply => reflectQuote(quote)
          case quote: RefTree => reflectQuote(quote)
          case _ => tree
        }
      case _ => tree
    }
  }

  private def reflectQuote(tree: Tree)(implicit ctx: Context): Tree = {
    if (!tree.tpe.derivesFrom(defn.MetaExpr) ||
        tree.symbol == defn.MetaQuote ||
        tree.symbol.isConstructor)
      return ref(defn.MetaSplice).appliedToType(tree.tpe).appliedTo(tree)
    val sym = tree.symbol
    try {
      val urls = ctx.settings.classpath.value.split(':').map(cp => java.nio.file.Paths.get(cp).toUri.toURL)
      val classLoader = new URLClassLoader(urls, getClass.getClassLoader)
      val clazz = classLoader.loadClass(sym.owner.showFullName)
      val args: List[AnyRef] = tree match {
        case Apply(_, args) =>
          def expr(tree: Tree, splices: List[RawExpr]): RawExpr = tree match {
            case tree @ Apply(_, quote :: Nil) if tree.symbol eq defn.MetaQuote =>
              new RawExpr(quote, splices)
            case tree @ Apply(Select(inner, _), spliced :: Nil) if tree.symbol eq defn.MetaExprSpliced =>
              expr(inner, expr(spliced, Nil) :: splices)
          }

          args.map {
            case Literal(Constant(c)) => c.asInstanceOf[AnyRef]
            case arg =>
              if (arg.symbol == defn.MetaQuote || arg.symbol == defn.MetaExprSpliced) expr(arg, Nil)
              else {
                val msg =
                  if (arg.tpe.derivesFrom(defn.MetaExpr)) "Quote needs to be explicit"
                  else "Value needs to be a explicit"
                ctx.error(msg, arg.pos)
                return ref(defn.MetaSplice).appliedToType(tree.tpe).appliedTo(tree)
              }
          }
        case _ => Nil
      }
      val paramClasses = sym.signature.paramsSig.map { param =>
        if (param.toString == "scala.Int") 0.getClass // TODO
        else classLoader.loadClass(param.toString)
      }
      val method = clazz.getDeclaredMethod(sym.name.toString, paramClasses: _*)
      val expr = method.invoke(null, args: _*).asInstanceOf[Expr[_]]

      foo(expr)
    } catch {
      case _: NoSuchMethodException =>
        ctx.error(s"Could not find macro ${sym.showFullName} in classpath", tree.pos)
        tree
      case _: ClassNotFoundException =>
        ctx.error(s"Could not find macro class ${sym.owner.showFullName} in classpath", tree.pos)
        tree
    }
  }

  private def foo(expr: Expr[_])(implicit ctx: Context): Tree = {
    val splices = expr.splices.map(foo)
    val code = expr match {
      case expr: RawExpr => expr.tree
      case expr: TastyExpr[_] => revealQuote(unpickle(expr.tasty))
    }
    spliceIn(code, splices)

  }

  private def spliceIn(code: Tree, splices: List[Tree])(implicit ctx: Context): Tree = {
    if (splices.isEmpty) code
    else {
      new TreeMap() {
        private[this] var splicesLeft = splices
        override def transform(tree: tpd.Tree)(implicit ctx: Context): tpd.Tree = {
          splicesLeft match {
            case splice :: rest =>
              if (tree.symbol eq defn.MetaSpliceHole) {
                splicesLeft = rest
                splice
              }
              else super.transform(tree)
            case Nil => tree
          }
        }
      }.transform(code)
    }
  }

  class RawExpr(val tree: Tree, val splices: List[RawExpr]) extends Expr[Any]
}
