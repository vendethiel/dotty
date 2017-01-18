case class Box(t: (String, Int, A))
case class A(i: Int, j: Int)
case class XX[X, Y](i: X, j: Y)

object Test {
  def main(args: Array[String]) = {
    val x1 = Box(("a", 2, A(3, 4)))

    x1 match {
      case Box((a3, b3, A(a1, a2))) =>
        assert(a3 == "a")
        assert(b3 == 2)
        assert(a1 == 3)
        assert(a2 == 4)
    }

    val x2 = ("a", 2)

    x2 match {
      case (a2, b2) =>
        assert(a2 == "a")
        assert(b2 == 2)
    }

    val x3 = ("a", 2, "c")

    x3 match {
      case (a3, b3, c3) =>
        assert(a3 == "a")
        assert(b3 == 2)
        assert(c3 == "c")
    }

    val x4 = ("a", 2, "c", 4)

    x4 match {
      case (a4, b4, c4, d4) =>
        assert(a4 == "a")
        assert(b4 == 2)
        assert(c4 == "c")
        assert(d4 == 4)
    }

    val x5 = ("a", 2, "c", 4, "e")

    x5 match {
      case (a5, b5, c5, d5, e5) =>
        assert(a5 == "a")
        assert(b5 == 2)
        assert(c5 == "c")
        assert(d5 == 4)
        assert(e5 == "e")
    }

    val x6 = ("a", 2, "c", 4, "e", 6)

    x6 match {
      case (a5, b5, c5, d5, e5, f5) =>
        assert(a5 == "a")
        assert(b5 == 2)
        assert(c5 == "c")
        assert(d5 == 4)
        assert(e5 == "e")
        assert(f5 == 6)
    }
  }

  def any = {
    val x: Any = null
    x match { case Box((a3, b3, A(a1, a2))) => () }
    x match { case (a2, b2) => () }
    x match { case (a3, b3, c3) => () }
    x match { case (a4, b4, c4, d4) => () }
    x match { case (a5, b5, c5, d5, e5) => () }
    x match { case (a5, b5, c5, d5, e5, f5) => () }
  }
}
