package dotty.meta

import TastyString._

final class Expr[+T](val tastyString: String) {
  def tasty: Array[Byte] = stringToTasty(tastyString)
}
