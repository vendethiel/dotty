package dotty.meta

object TastyString {

  def tastyToString(bytes: Array[Byte]): String = {
    val chars = new Array[Char](bytes.length)
    for (i <- bytes.indices) chars(i) = (bytes(i) & 0xff).toChar
    new String(chars)
  }

  def stringToTasty(str: String): Array[Byte] = {
    val bytes = new Array[Byte](str.length)
    for (i <- str.indices) bytes(i) = str.charAt(i).toByte
    bytes
  }

}
