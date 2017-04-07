object Test {
  def main(args: Array[String]): Unit = {
    val t1 = (1, "s")

    t1 match {
      case (i, s) => i
    }

    val t2 = new Some(new Tuple2(t1._1, t1._2))
  }
}
