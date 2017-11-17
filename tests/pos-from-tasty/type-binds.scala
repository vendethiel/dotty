trait Map2[K <: Int] {
  def foo = {
    this match {
      case that: Map2[b] => null.asInstanceOf[b]
    }
    ()
  }
}
