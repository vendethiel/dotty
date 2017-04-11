object O {
  Map.empty[Int, List[String]]
     .map { case (k, v) => ??? }

  val tuple = new Tuple2(1, "")
  val some  = new Some(new Tuple2(tuple._1, tuple._2))
}

// Source

//     Map.empty[Int, List[String]]
//        .map { case (k, v) => ??? }

// Before simplify:

//     ...
//     case val o31: Option[(Int, List[String])] =
//       Tuple2.unapply[Int, List[String]](selector11)
//     ...

// After simplify:

//     case val o31: Option[(Int, List[String])] =
//       new Some[(Int, List[String])](
//         new Tuple2[Int, List[String]](selector11._1, selector11._2))

// Before back-end:

//     case val o31: Option =
//       new Some(new Tuple2(scala.Int.box(selector11._1()), selector11._2()))
