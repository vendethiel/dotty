object Test {
  def m(f: String => Unit) = 0
  def m(f: Int => Unit) = 0
  def foo: Unit = {
    m { s => case class Foo() }
  }
}
