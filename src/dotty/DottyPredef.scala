package dotty

import scala.reflect.runtime.universe.TypeTag
import scala.reflect.ClassTag
import scala.collection.mutable.ArrayOps
import scala.runtime.ScalaRunTime.arrayElementClass
import scala.collection.mutable.WrappedArray
import dotty.runtime.vc._
import scala.Predef.???

/** unimplemented implicit for TypeTag */
object DottyPredef {
  implicit def typeTag[T]: TypeTag[T] = ???

  implicit def arrayTag[T](implicit ctag: ClassTag[T]): ClassTag[Array[T]] =
    ctag.wrap

  def wrapVCArray[T](xs: Array[T]): WrappedArray[T] = {
    new WrappedArray[T] {
      val array = xs
      lazy val elemTag = ClassTag[T](arrayElementClass(array.getClass))
      def length: Int = array.length
      def apply(index: Int): T = array(index)
      def update(index: Int, elem: T) = {
        array(index) = elem
      }
    }
  }

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

  def refVCArray[T /*<: AnyVal*/](xs: Array[T]): ArrayOps[T] = {
    new ArrayOps[T] {
      val array = xs
      lazy val elemTag = ClassTag[T](arrayElementClass(array.getClass))
      def length: Int = array.length
      def apply(index: Int): T = array(index)
      def update(index: Int, elem: T) = {
        array(index) = elem
      }
      //TODO - add the implementation for newBuilder
      def newBuilder = ???
    }
  }

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
}
