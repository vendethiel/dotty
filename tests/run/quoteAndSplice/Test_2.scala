
import dotty.meta._
import quotes.Quotes_1._

object Test {
  def main(args: Array[String]): Unit = {
    splice(printHello)
    splice(inlinePrintHello)
  }
}

