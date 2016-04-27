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

  def opsVCArray[T /*<: AnyVal*/](xs: Array[T]): ArrayOps[T] =
    new VCArrayOps[T](xs)
}
