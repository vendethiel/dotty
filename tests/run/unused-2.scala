import dotty.unused

object Test {

  def main(args: Array[String]): Unit = {
    try {
      fun2(???)
      throw new Exception
    } catch {
      case e: NotImplementedError => println("OK")
    }
  }

  def fun2(@unused bottom: Nothing): Unit = {
    println("fun2")
  }
}
