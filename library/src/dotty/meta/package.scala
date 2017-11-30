package dotty

/**
 *  @author Nicolas Stucki
 */
package object meta {

  def quote[T](code: T): Expr[T] =
    throw new Error("Quoted expression was not pickled")

  def splice[T](expr: Expr[T]): T =
    throw new Exception("Runtime splicing is not supported yet")

}
