
import dotty.meta._
import quotes.Quotes_1._

object Test {
  def main(args: Array[String]): Unit = {
    splice(printHello) // get quote through reflective call to printHello
    splice(inlinePrintHello)  // just inline
    splice(printHello(quote("world"))) // get quote through reflective call to printHello
  }
}

