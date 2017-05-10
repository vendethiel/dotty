class Foo[T <: U, U <: T] // error: illegal cyclic reference: upper bound U of type T refers back to the type itself

class Bar[T >: T] // error: illegal cyclic reference: lower bound T of type T refers back to the type itself

object O {

  type C[T]
  type U = V // error: cyclic reference
  type V = C[C[U]]
  type W <: C[V] // ok

}
class A {
  val x: T = ???
  type T <: x.type // error: cyclic reference involving value x
}

class B {
  type T <: x.type // error: illegal cyclic reference: upper bound B.this.T(B.this.x) of type T refers back to the type itself
  val x: T = ???
}

class C {
  final val x: D#T = ???
  class D {
    type T <: x.type // error: cyclic reference involving value x
    val z: x.type = ???
  }
}

class E {
  class F {
    type T <: x.type // error: cyclic reference involving value x
    val z: x.type = ??? // old-error: not stable
  }
  lazy val x: F#T = ???
}

class T1 {
  type X = (U, U) // error: cycle
  type U = X & Int
}
class T2 {
  type X = (U, U) // error: cycle
  type U = X | Int
}
object T12 {
  ??? : (T1 {})#U // old-error: conflicting bounds
  ??? : (T2 {})#U // old-error: conflicting bounds
}

class C1 extends D1 // error: C1 extends itself
class D1 extends C1

class C2 extends C2 // error: C2 extends itself

class C3 extends T3 // error: C3 extends itself
class C4 extends C3
trait T3 extends C4

// #2403
// pack.scala
package foo {

  package object bar {
    type Foo = foo.bar.Foo // error: cyclic reference
  }
}

// Foo.scala
package foo.bar {

  //class Foo

}

// usage.scala
package boobar {

  import foo.bar._

  object Test {
    val x: Foo = ???
  }
}
