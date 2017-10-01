import dotty.unused

object Test {

  def main(args: Array[String]): Unit = {
    fun1(42)
  }

  def fun1(@unused boo: Int): Unit = {
    println("fun1")
  }
}
