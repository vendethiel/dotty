class Foo {

  def foo: Unit = {
    dotty.Quote[Int] {
      identity(4)
    }
  }
}