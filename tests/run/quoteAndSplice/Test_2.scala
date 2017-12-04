
import dotty.meta._
import quotes.Quotes_1._

object Test {
  def main(args: Array[String]): Unit = {
    ~printHello // get quote through reflective call to printHello
    ~inlinePrintHello  // just inline
    ~printHello(quote("world")) // get quote through reflective call to printHello
  }
}

