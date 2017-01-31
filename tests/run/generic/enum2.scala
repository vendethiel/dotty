package generic

import Shapes._

sealed trait Col
final case object Red   extends Col
final case object Green extends Col
final case object Blue  extends Col

object Col {
  type ColShape =
    Sum[Red.type, Sum[Green.type, Blue.type]]

  implicit val ColShape: Col `shaped` ColShape =
    new (Col `shaped` ColShape) {
      def toShape(x: Col) = x match {
        case Red   => Fst(Red)
        case Green => Snd(Fst(Green))
        case Blue  => Snd(Snd(Blue))
      }

      def fromShape(x: ColShape) = x match {
        case Fst(_: Red.type)        => Red
        case Snd(Fst(_: Green.type)) => Green
        case Snd(Snd(_: Blue.type))  => Blue
        case _ => ???
      }
    }
}

/** The unique value of the type T where T is a type with a single inhabitant,
  * such class.type or literal singleton types.
  */
final case class ValueOf[T](value: T) extends AnyVal

object ValueOf {
  // To be compiler generated on demend, much like ClassTag & co
  implicit val valueOfRed: ValueOf[Red.type] = ValueOf(Red)
  implicit val valueOfGreen: ValueOf[Green.type] = ValueOf(Green)
  implicit val valueOfBlue: ValueOf[Blue.type] = ValueOf(Blue)
}

/** Type class to obtain all values of an enumeration. */
trait Values[T] {
  def values: collection.immutable.List[T]
}

object Values {
  implicit def shaped[T, Repr]
    (implicit
      g: T `shaped` Repr,
      v: Aux[T, Repr]
    ): Values[T] =
      new Values[T] { def values = v.values }

  // Type used locally for the type-level computation
  trait Aux[X, Repr] {
    def values: collection.immutable.List[X]
  }

  object Aux {
    implicit def sum[X, T, U]
      (implicit
        ev1: => Aux[X, T],
        ev2: => Aux[X, U]
      ): Aux[X, Sum[T, U]] =
        new Aux[X, Sum[T, U]] {
          def values = ev1.values ++ ev2.values
        }

    implicit def leaf[X, T <: X](implicit v: ValueOf[T]): Aux[X, T] =
      new Aux[X, T] {
        def values = collection.immutable.List[X](v.value)
      }
  }
}
