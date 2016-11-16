object Fail {
  case class So[+T]()

  def main(args: Array[String]): Unit = {
    val x = So[_]  // error
    So[Int]() match {
      case p@So[_] => Console.println("Yep")  // error
      case _       => Console.println("Nope")
    }
  }
}
