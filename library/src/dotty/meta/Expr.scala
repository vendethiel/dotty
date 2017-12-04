package dotty.meta

trait Expr[+T] { self =>
  def splices: List[Expr[_]]
  def unary_~(): T = throw new Exception("Runtime splicing is not supported yet")
}
