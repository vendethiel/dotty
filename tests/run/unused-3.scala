import dotty.unused

object Test {
  
  def main(args: Array[String]): Unit = {
    fun(f(1))(f(2))(f(3))(f(4))
  }

  def f(x: Int): Int = {
    println(x)
    x
  }

  def fun(@unused a: Int)(b: Int)(@unused c: Int)(d: Int): Unit = {
    println("fun")
  }
}
