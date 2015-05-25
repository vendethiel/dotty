package dotty

import scala.reflect.runtime.universe.TypeTag
import scala.reflect.ClassTag
import scala.Predef.???

/** unimplemented implicit for TypeTag */
object DottyPredef {
  implicit def typeTag[T]: TypeTag[T] = ???

  implicit def arrayTag[T](implicit ctag: ClassTag[T]): ClassTag[Array[T]] =
    ctag.wrap

  // This implicit will never be used for arrays of primitives because
  // the wrap*Array in scala.Predef have a higher priority.
  implicit def wrapVCArray[T <: AnyVal](xs: Array[T]): WrappedArray[T] =
    new WrappedArray[T] {
      val array = xs
      lazy val elemTag = ClassTag[T](arrayElementClass(array.getClass))
      def length: Int = array.length
      def apply(index: Int): T = array(index)
      def update(index: Int, elem: T) { array(index) = elem }
    }
}
