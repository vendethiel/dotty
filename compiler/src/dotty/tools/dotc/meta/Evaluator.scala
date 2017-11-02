package dotty.tools.dotc
package meta

import core._
import Contexts._, Symbols._
import ast.Trees._

object Evaluator {
  import ast.tpd._

  /** Evaluate code given in `t`.
   *  E.g. if `t` is the tree representation of
   *
   *    scala.Arrays.mapImpl('u, 'xs, 'f)(ctx)
   *
   *  we should reflectively call `mapImpl` with `Arrays` as receiver
   *  and `u`, `xs`, `f` and the reflection context as arguments.
   *  The reflection contxt is some simplified and restricted view of
   *  the compiler context `ctx`. We still need to decide what that is.
   *
   *  If `t` is some other tree representation we can make a "best effort"
   *  to interpret that as well, but at least initially it's not required.
   */
  def eval(t: Tree)(implicit ctx: Context): Any = ???

}

