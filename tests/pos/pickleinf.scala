class Bar[N] {
  def bar(name: N, dummy: Int = 42): Int = 0
}

object Test {
  def test(): Unit = {
    (new Bar).bar(10)
  }
}
