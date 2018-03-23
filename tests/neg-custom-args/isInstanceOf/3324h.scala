object Test {
  trait Marker
  def foo[T](x: T) = x match {
    case _: (T & Marker)       => // no warning
    // case _: T with Marker => // scalac emits a warning
    case _ =>
  }

  def bar(x: Any) = x.isInstanceOf[List[String]]  // error
}