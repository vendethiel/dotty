object Test {
  import Unused._
  import Naturals._
  import Materialiser._

//  def proveExists1[N <: Nat](implicit n: N): Boolean = {
//    materialise(n) match {
//      case n: Z.type => println("proveExists1: 0")
//      case n: S[_]   => println("proveExists1: " + n)
//    }
//    true
//  }

//  def proveExists2[N <: Nat](implicit n: N): Unused[Boolean] = {
//    import Materialiser._
//    materialise(n) match {
//      case n: Z.type => println("proveExists2: 0")
//      case n: S[_]   => println("proveExists2: " + n)
//    }
//    true
//  }

  def proveExists3[N <: Nat](implicit n: Unused[N]): Unused[Boolean] = {
    import Materialiser._
    materialise(n) match {
      case n: Z.type => println("proveExists3: 0")
      case n: S[_]   => println("proveExists3: " + n)
    }
    true
  }

//  def proveExists4[N <: Nat](implicit n: Unused[N]): Boolean = {
//    materialise(n) match {
//      case n: Z.type => println("proveExists4: 0")
//      case n: S[_]   => println("proveExists4: " + n)
//    }
//    true
//  }

  def main(args: Array[String]): Unit = {
//    proveExists1[S[Z.type]]
//    proveExists2[S[Z.type]]
    proveExists3[S[Z.type]]
//    try {
//      proveExists4[S[Z.type]]
//      assert(false)
//    } catch {
//      case e: TryedToUseUnusedExeption => // OK
//    }
  }

}

object Naturals {

  sealed trait Nat

  object Z extends Nat {
    override def toString: String = "0"
  }

  class S[N <: Nat](n: N) extends Nat {
    override def toString: String = "1+" + n
  }

  implicit def zero: Z.type = printed(Z)

  implicit def succ[N <: Nat](implicit n: N): S[N] = printed(new S(n))

  private def printed[X](x: X): X = { println(x); x }
}

object Unused extends Phantom {

  type Unused[+X] <: this.Any

  implicit def unusedImplicit[X](implicit x: X): Unused[X] = this.assume
  implicit def unused[X](x: X): Unused[X] = this.assume

  object Materialiser {
    def materialise[X](x: Unused[X]): X = throw new TryedToUseUnusedExeption
    def materialise[X](x: X): X = x
  }

  class TryedToUseUnusedExeption extends Exception("cannot materialize unused value")
}

