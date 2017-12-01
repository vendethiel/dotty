package dotty.meta

final class Expr[+T](val tastyString: String) {
  def tasty: Array[Byte] = Expr.stringToBytes(tastyString)
}

object Expr {
  def apply(tasty: Array[Byte]): Expr[Any] = new Expr(bytesToString(tasty))

  // TODO improve string representation
  private def bytesToString(bytes: Array[Byte]): String = bytes.map(b => f"$b%02X").mkString
  private def stringToBytes(str: String): Array[Byte] = str.sliding(2, 2).map(a => Integer.parseInt(a.mkString, 16).toByte).toArray

}
