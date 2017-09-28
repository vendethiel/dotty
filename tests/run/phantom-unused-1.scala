import dotty.unused

object Test {

  def main(args: Array[String]): Unit = {
    fun1(42: @unused)
  }

  def fun1(boo: Int @unused): Unit = {
    println("fun1")
  }
}
