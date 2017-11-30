package dotty

object Quote {
  def apply[T](quote: T): Expr[T] = ???
  class Expr[T](/*inline*/ bytes: String)
}
