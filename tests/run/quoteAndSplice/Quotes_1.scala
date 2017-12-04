package quotes
import dotty.meta._

object Quotes_1 {
  def printHello: Expr[Unit] = quote(println("Hello"))
  inline def inlinePrintHello: Expr[Unit] = quote(println("Hello"))
  def printHello(x: Expr[String]): Expr[Unit] = quote(println("Hello " + ~x + ~quote("!")))
}
