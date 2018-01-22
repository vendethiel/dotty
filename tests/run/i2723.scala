trait App(init: implicit Array[String] => Unit) {
  inline def main(args: Array[String]): Unit = init(args)
}

object Test {
  val f: implicit Array[String] => Unit = println(implicitly[Array[String]].deep)
  def main(args: Array[String]) = {
    val app = new App(f) {}
    app.main(Array("hello", "world!"))
  }
}
