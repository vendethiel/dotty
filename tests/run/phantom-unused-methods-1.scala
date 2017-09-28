import dotty.unused

object Test {

  def main(args: Array[String]): Unit = {
    fun(phantomFun1())
  }

  def fun(top: Int @unused): Unit = ()

  @unused def phantomFun1(): Int = 42
}
