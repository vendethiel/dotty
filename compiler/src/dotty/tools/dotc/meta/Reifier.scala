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
   *
   *  This is easy for simple cases but gets progressively harder as we have to model features like
   *  nested objects and classes. A promising alternative would be to read instead the TASTY tree
   *  that represents the quote at runtime. But the latter approach also has challenges to overcome:
   *
   *    - how to find the tree? We need to first identify the classfile, pull out the full TASTY info
   *      and then navigate to the subtree that represents the quote.
   *    - how to access the TASTY reader? It would have to be packaged as a module accessible
   *      from user code.
   *    - how to deal with embedded splices? We have to identify them somehow and then substitute them out
   *      with computed trees.
   *
   *  If we can solve these challenges, option (2) looks ultimately easier and more useful.
   */
  def reify(t: Tree)(implicit ctx: Context): Tree = ???

}
