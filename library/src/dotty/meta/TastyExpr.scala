package dotty.meta

import TastyString._

class TastyExpr[+T](val tastyString: String, val splices: List[Expr[_]]) extends Expr[T] {
  def tasty: Array[Byte] = stringToTasty(tastyString)
  def spliced(e: Expr[_]): TastyExpr[T] = new TastyExpr[T](tastyString, e :: splices)
}
