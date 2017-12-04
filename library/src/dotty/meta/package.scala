package dotty

/**
 *  @author Nicolas Stucki
 */
package object meta {

  def quote[T](code: T): Expr[T] =
    throw new Error("Quoted expression was not pickled")

  def spliceHole[T]: T =
    throw new Exception(s"Splice hole was not filled with expression")

}
