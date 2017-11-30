import dotty.meta._

class Foo {

  def foo: Unit = {
    splice[Int](quote[Int] {
      identity(4)
    })

  }
}