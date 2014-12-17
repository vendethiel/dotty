package bugreport

object BugReport {
  trait Type {
    type DT <: Type
  }

  trait BaseInt extends Type {
    type DT = BaseInt
  }

  trait Exp[TP <: Type]

  class Num extends Exp[BaseInt]

  def derive[T <: Type](term: Exp[T]): Any = {

    val res1 = term match { case _: Num => (??? : Exp[T#DT]) }
    res1: Exp[T#DT] // fails, the prefix of the type projection in the case body underwent unwanted GADT refinement

    type X = T#DT
    val res2 = term match { case _: Num => (??? : Exp[X]) }
    res2: Exp[T#DT] // works
  }
}
