import dotty.unused

object Test {

  def main(args: Array[String]): Unit = {
    @unused def !!! : Nothing = ???
    fun2(!!!)
  }

  def fun2(bottom: Nothing @unused): Unit = {
    println("fun2")
  }
}
