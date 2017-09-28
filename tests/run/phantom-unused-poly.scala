import dotty.unused

object Test {

  def main(args: Array[String]): Unit = {
    polyfun2(new Object: @unused)
    polyfun2("a": @unused)
    polyfun2(42: @unused)
  }

  def polyfun2[G <: AnyRef](p: G @unused): Unit = {
    println("polyfun2")
  }
}
