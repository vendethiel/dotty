package dotty.tools.dotc.config

object Printers {

  class Printer {
    def println(msg: => String): Unit = System.out.println(msg)
  }

  object noPrinter extends Printer {
    override def println(msg: => String): Unit = ()
  }

  val default: Printer = new Printer

  val constr: Printer = noPrinter
  val core: Printer = noPrinter
  val checks: Printer = noPrinter
  val config: Printer = noPrinter
  val cyclicErrors: Printer = noPrinter
  val dottydoc: Printer = noPrinter
  val exhaustivity: Printer = noPrinter
  val gadts: Printer = noPrinter
  val hk: Printer = noPrinter
  val implicits: Printer = noPrinter
  val implicitsDetailed: Printer = noPrinter
  val inlining: Printer = noPrinter
  val interactiv: Printer = noPrinter
  val overload: Printer = noPrinter
  val patmatch: Printer = noPrinter
  val pickling: Printer = noPrinter
  val plugins: Printer = noPrinter
  val simplify: Printer = noPrinter
  val subtyping: Printer = noPrinter
  val transforms: Printer = noPrinter
  val typr: Printer = noPrinter
  val unapp: Printer = noPrinter
  val variances: Printer = noPrinter
}
