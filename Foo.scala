import scala.quoted.Expr

object Foo {
  def impl(i: Int): Expr[Int] = i + 3 // stage -1
  inline def foo(inline i: Int): Int = ~impl(i) // stage -1 or 0
  def bar() = foo(42) // stage 0

}
