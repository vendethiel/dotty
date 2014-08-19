case class Foo(x: Int) {

  override def equals(other: Any) = other match {
    case x$0: Foo => this.x == x$0.x
  }
}
