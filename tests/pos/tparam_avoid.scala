class Foo[A]

class Outer {
  class Bar[B] extends Foo[B]
}

object Test {
  def test = {
    val foo: Foo[Int] = {
      val o = new Outer
      new o.Bar[Int]//: Foo[Int]
    }
  }
}
