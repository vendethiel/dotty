package dotty

import scala.collection.generic.CanBuildFrom
import scala.reflect.runtime.universe.TypeTag
import scala.reflect.ClassTag
import scala.collection.mutable.{ArrayBuilder, ArrayOps, WrappedArray}
import dotty.runtime.vc._
import scala.Predef.???

/** unimplemented implicit for TypeTag */
object DottyPredef {
  implicit def typeTag[T]: TypeTag[T] = ???

  implicit def arrayTag[T](implicit ctag: ClassTag[T]): ClassTag[Array[T]] =
    ctag.wrap

  def wrapVCArray[T](xs: Array[T]): WrappedArray[T] =
    new VCWrappedArray[T](xs)

  //def to substitute scala.Predef.genericWrapArray
  def genericWrapArray2[T](xs: Array[T]): WrappedArray[T] = {
    xs match {
      case x: Array[T] if x.isInstanceOf[VCArrayPrototype[_]] => wrapVCArray(xs)
      //impl of scala.Predef.genericWrapArray(x)
      case x: Array[T] =>
        if (xs eq null) null
        else WrappedArray.make(xs)
      case _ => ???
    }
  }

  def refVCArray[T /*<: AnyVal*/](xs: Array[T]): ArrayOps[T] =
    new VCArrayOps[T](xs)

  //def to substitute scala.Predef.genericArrayOps
  def genericArrayOps2[T](xs: Array[T]): ArrayOps[T] = (xs match {
    case x: Array[T] if x.isInstanceOf[VCArrayPrototype[_]] => refVCArray(x)
    //impl of scala.Predef.genericWrapArray(x)
    case x: Array[AnyRef] => scala.Predef.refArrayOps[AnyRef](x)
    case x: Array[Boolean] => scala.Predef.booleanArrayOps(x)
    case x: Array[Byte] => scala.Predef.byteArrayOps(x)
    case x: Array[Char] => scala.Predef.charArrayOps(x)
    case x: Array[Double] => scala.Predef.doubleArrayOps(x)
    case x: Array[Float] => scala.Predef.floatArrayOps(x)
    case x: Array[Int] => scala.Predef.intArrayOps(x)
    case x: Array[Long] => scala.Predef.longArrayOps(x)
    case x: Array[Short] => scala.Predef.shortArrayOps(x)
    case x: Array[Unit] => scala.Predef.unitArrayOps(x)
    case null => null
  }).asInstanceOf[ArrayOps[T]]

  implicit def canBuildFrom2[T](implicit t: ClassTag[T]): CanBuildFrom[Array[_], T, Array[T]] = {
    t match {
      case _: VCIntCompanion[_] | _: VCShortCompanion[_] |
           _: VCLongCompanion[_] | _: VCByteCompanion[_] |
           _: VCBooleanCompanion[_] | _: VCCharCompanion[_] |
           _: VCFloatCompanion[_] | _: VCDoubleCompanion[_] |
           _: VCObjectCompanion[_] =>
        new CanBuildFrom[Array[_], T, Array[T]] {
          def apply(from: Array[_]) = new VCArrayBuilder[T]()(t).asInstanceOf[ArrayBuilder[T]]
          def apply() = new VCArrayBuilder[T]()(t).asInstanceOf[ArrayBuilder[T]]
        }
      case _ => scala.Array.canBuildFrom[T](t)
    }
  }
}
