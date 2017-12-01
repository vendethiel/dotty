import dotty.meta._

class Foo {

  def foo: Expr[Int] = quote[Int](identity(4))
  inline def bar: Expr[Int] = quote[Int](identity(4))
  def baz: Unit = {
    splice[Int](quote[Int](identity(4)))
    splice[Int](foo)
    splice[Int](bar)
  }
}
