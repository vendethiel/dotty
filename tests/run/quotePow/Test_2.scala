
import dotty.meta._
import quotes.Quotes_1._

object Test {
  def main(args: Array[String]): Unit = {
    val x: Int = 4
    println(splice(pow(quote(3), 5)))
    println(splice(pow(quote(splice(quote(2 + splice(quote(4)) + splice(quote(7)))) + splice(quote(3))), 3)))
  }
}

