
import dotty.tools.dotc.quoted.Runners._

import scala.quoted._

object Test {
  def main(args: Array[String]): Unit = {
    val expr = '{
      val a = 3
      2 + a
    }
    println(expr.show)
    val n = 10
    def timed(thunk: => Unit, name: String) = {
      val t0 = System.currentTimeMillis()
      thunk
      val t = System.currentTimeMillis() - t0

      // println(s"time of $name: ${t.toDouble / 1000}")
    }
    timed ({
      for (_ <- 0 to n)
        expr.run
    },
      "expr.run total"
    )
    val expr2 = dotty.tools.dotc.quoted.Runners.compiled(expr, true)
    println(expr2.show)
    timed( {
      for (_ <- 0 to n)
        expr2.run
    },
      "expr2.run total"
    )
  }
}
