package dotty.meta

import TastyString._

final class Expr[+T](val tastyString: String, val splices: List[Expr[_]]) {
  def tasty: Array[Byte] = stringToTasty(tastyString)
  def spliced(e: Expr[_]): Expr[T] = new Expr[T](tastyString, e :: splices)
}
