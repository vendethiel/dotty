package dotty.tools.dotc
package meta

import ast._
import core._
import Contexts._, Symbols._

object Reifier {
  import ast.tpd._

  /** Generate code for constructing tree `t` at runtime. E.g.
   *  if `t` is
   *
   *     Apply(Select(Ident(x), "+"), Literal(1) :: Nil)
   *
   *  we should generate the tree representing
   *
   *      tpd.ref(<symbol of x>).select(<symbol of "+">).appliedTo(tpd.Literal(Constant.apply(1)) :: Nil)
   */
  def reify(t: Tree)(implicit ctx: Context): Tree = ???

}
