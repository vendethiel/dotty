package dotty.meta

trait Expr[+T] {
  def splices: List[Expr[_]]
}
