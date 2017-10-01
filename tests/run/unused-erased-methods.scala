import dotty.unused

import scala.util.Try

object Test {

  def main(args: Array[String]): Unit = {
    val foo = new Foo

    // check that parameters have been removed from the functions
    // (would fail with NoSuchMethodException if not erased)
//    foo.getClass.getDeclaredMethod("fun1")
//    foo.getClass.getDeclaredMethod("fun2", Array(classOf[String]): _*)

//    assert(Try(foo.getClass.getDeclaredMethod("fun3")).isFailure)
    ()
  }
}

class Foo {
  def fun1(@unused b: Int): Unit = ()
  def fun2(@unused b: Int)(s: String): Unit = ()
  @unused def fun3(): Int = 42
}
