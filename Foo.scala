import scala.quoted._

import dotty.tools.dotc.quoted.Toolbox._

object Foo {
  def main(args: Array[String]): Unit = {
//    println(asSucc(Int.MaxValue).show)
    println(asSucc(250).show)
    println(asSucc(3).show)
  }

  def asSucc(n: Long): Expr[Long] =
    if (n == 0L) '(0)
    else '(~asSucc(n-1) + 1L)

}
