package dotty.tools.dotc.quoted

import scala.quoted.Expr
import scala.quoted.Liftable.ConstantExpr
import scala.runtime.quoted._

/** Default runners for quoted expressions */
object Runners {

  implicit def runner[T]: Runner[T] = new Runner[T] {

    def run(expr: Expr[T]): T = expr match {
      case expr: ConstantExpr[T] => expr.value
      case expr: CompiledQuoted[T] => expr.run
      case _ => new QuoteDriver().run(expr)
    }

    def show(expr: Expr[T]): String = expr match {
      case expr: ConstantExpr[T] => expr.value.toString
      case expr: CompiledQuoted[T] => "<compiled code>"
      case _ => new QuoteDriver().show(expr)
    }
  }

  def compiled[T](expr: Expr[T], showable: Boolean = false): Expr[T] = expr match {
    case expr: ConstantExpr[T] => expr
    case _ => new QuoteDriver().compiled(expr, showable)
  }
}
