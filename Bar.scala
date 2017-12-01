import dotty.meta._
object Bar {

  inline def twice(x: Expr[Int]): Expr[Int] = {
    quote(2 * splice(x))
  }

  def foo: Unit = {
    splice(twice(quote(4)))
  }

}
