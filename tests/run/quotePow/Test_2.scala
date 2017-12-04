
import dotty.meta._
import quotes.Quotes_1._

object Test {
  def main(args: Array[String]): Unit = {
    val x: Int = 4
    println(~pow(quote(3), 5))
    println(~pow(quote(2 + ~quote(4 + ~quote(7)) + ~quote(3)), 3))
    println(~pow(quote(x), 5))
    println(~pow(quote(x + ~quote(x + 1)), 5))
  }
}

