class Foo {

  def foo: Unit = {
    dotty.Splice[Int](dotty.Quote[Int] {
      identity(4)
    })

  }
}