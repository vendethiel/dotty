trait Iterable2[A] {
  def flatMap[B](f: A => B): Iterable2[B] = ???
}
trait SortedMap2[K, V] extends Iterable2[(K, V)] {
  def flatMap[K2, V2](f: ((K, V)) => (K2, V2))(implicit ordering: Ordering[K2]): SortedMap2[K2, V2] = ???
  def foo: SortedMap2[Int, Int] = flatMap { kv => (0, 0) }
}

trait SortedMap[K, V] {
  def collect[B](pf: PartialFunction[(K, V), B]): Int = 0
  def collect[K2 : Ordering, V2](pf: PartialFunction[(K, V), (K2, V2)]): Int = 0
  def foo(xs: SortedMap[String, scala.Int]) = xs.collect { case (k, v) => 1 }
}

trait SortedMap3[K, V] {
  def collect[B](pf: PartialFunction[(K, V), B]): Int = 0
  def collect[K2 : Ordering, V2](pf: Function1[(K, V), (K2, V2)]): Int = 0
  def foo(xs: SortedMap3[String, scala.Int]) = xs.collect { case (k, v) => 1 }
}
