
import dotty.meta._
import quotes.Quotes_1._

object Test {
  def main(args: Array[String]): Unit = {
    val x: Int = 4
    println(splice(pow(quote(5), 5)))
  }
}

