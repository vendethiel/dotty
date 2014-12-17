// Example by @retronym in #271
// The following works:
trait Type1 {
  type DT <: Type1
}

trait Exp1[T <: Type1] {
  def derive: Exp1[T#DT]
}

// But this causes a cycle in findMember
trait Type2 {
  type DT <: Type2
}

trait Exp2[type T <: Type2] {
  def derive: Exp2[T#DT]
}

