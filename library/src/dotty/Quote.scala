package dotty

object Quote {
  def apply[T](quote: T): Expr[T] = ???
  class Expr[T](/*inline*/ val bytes: String)
}

object Splice {
  def apply[T](expr: Quote.Expr[T]): T = ???
}
