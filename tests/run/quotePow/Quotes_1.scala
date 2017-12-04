package quotes
import dotty.meta._

object Quotes_1 {

  def pow[T<: Int](x: Expr[Int], inline n: T): Expr[Int] = powRec(x, n)

  private def powRec(x: Expr[Int], n: Int): Expr[Int] = {
    if (n == 0) quote(1)
    else if (n % 2 == 0) quote {
      val a: Int = ~powRec(x, n / 2)
      a * a
    } else quote {
      val a: Int = ~powRec(x, n - 1)
      a * ~x
    }
  }

}
