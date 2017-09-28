import dotty.unused

object Test {

  def main(args: Array[String]): Unit = {
    fun2.pacFun4(inky: @unused)
  }

  def pacFun4(clyde: Int @unused) = {
    println("pacFun4")
  }

  @unused def inky: Int = {
    println("inky") // in erased function
    42
  }

  def fun2 = {
    println("fun")
    this
  }
}
