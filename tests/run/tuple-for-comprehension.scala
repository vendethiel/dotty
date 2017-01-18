object Test {
  def main(args: Array[String]) = {
    assert(2 == (for {
      x1 <- Some(1)
      x2 = x1
    } yield x2))

    assert(2 == (for {
      x1 <- Some(1)
      x2 = x1
      x3 = x1
    } yield x3))

    assert(2 == (for {
      x1 <- Some(1)
      x2 = x1
      x3 = x1
      x4 = x1
    } yield x4))

    assert(2 == (for {
      x1 <- Some(1)
      x2 = x1
      x3 = x1
      x4 = x1
      x5 = x1
    } yield x5))

    assert(2 == (for {
      x1 <- Some(1)
      x2 = x1
      x3 = x1
      x4 = x1
      x5 = x1
      x6 = x1
    } yield x6))
  }
}
