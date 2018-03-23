trait Bar[F[_]]
trait Foo[A]
trait Qux[A] extends Foo[A]
object Qux {
  implicit def string: Qux[String] = ???
  implicit def bar: Bar[Qux] = ???
}

case class Problem[A](a: A)
object Problem {
  import Qux._
  implicit def deriv[F[_]](implicit bla: Bar[F], baz: F[String]): F[Problem[String]] = ???
  implicitly[Foo[Problem[String]]](deriv)
}
