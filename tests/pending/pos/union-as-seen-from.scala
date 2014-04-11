class Super[T]

class A[T] {
  def f(x: T): T
}

class B[T] {
  def f(x: T): T
}

abstract class C[T] {

  val x: A[T] | B[T]

  val y = x.f

}

class D extends C[String] {

  def g: String => String = x.f

}




