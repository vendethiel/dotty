package dotty.tools.dotc
package ast

import core._
import Contexts.Context
import Decorators._
import util.Positions._
import Names._
import Trees._

import scala.collection.mutable.HashMap

object DiffAST {
  import untpd._

  var used: Boolean = false

  val newDef = new HashMap[Name, DefDef]

  def apply(before: Tree, after: Tree)(implicit ctx: Context): Unit = (before, after) match {
    case (PackageDef(pid1, stats1), PackageDef(pid2, stats2)) =>
      if (pid1 != pid2) {
        ctx.echo(s"DiffAST: packages do not match: $pid1 != $pid2")
        used = false
        return
      }
      if (stats1.length != stats2.length) {
        ctx.echo(s"DiffAST: Removing or adding classes is not supported")
        used = false
        return
      }
      (stats1, stats2).zipped.foreach(apply)
    case (TypeDef(name1, rhs1), TypeDef(name2, rhs2)) =>
      if (name1 != name2) {
        ctx.echo(s"DiffAST: types do not match: $name1 != $name2")
        used = false
        return
      }
      apply(rhs1, rhs2)
    case (before @ Template(constr1, parents1, self1, _),
           after @ Template(constr2, parents2, self2, _)) =>
      if (constr1 != constr1) {
        ctx.echo(s"DiffAST: constructors do not match: $constr1 != $constr2")
        used = false
        return
      }
      if (parents1 != parents1) {
        ctx.echo(s"DiffAST: parents do not match: $parents1 != $parents2")
        used = false
        return
      }
      if (self1 != self1) {
        ctx.echo(s"DiffAST: self types do not match: $self1 != $self2")
        used = false
        return
      }
      val body1 = before.body
      val body2 = after.body
      if (body1.length != body1.length) {
        ctx.echo(s"DiffAST: Removing or adding methods is not supported")
        used = false
        return
      }
      (body1, body2).zipped.foreach(apply)
    case (before @ DefDef(name1, tparams1, vparamss1, tpt1, _),
           after @ DefDef(name2, tparams2, vparamss2, tpt2, _)) =>
      if (name1 != name2) {
        ctx.echo(s"DiffAST: def names do not match: $name1 != $name2")
        used = false
        return
      }
      if (tparams1 != tparams2) {
        ctx.echo(s"DiffAST: def $name1 has type parameters that do not match: $tparams1 != $tparams2")
        used = false
        return
      }
      if (vparamss1 != vparamss2) {
        ctx.echo(s"DiffAST: def $name1 has value parameters that do not match: $vparamss1 != $vparamss2")
        used = false
        return
      }
      val rhs1 = before.rhs
      val rhs2 = after.rhs
      if (rhs1 != rhs2) {
        ctx.echo(s"DiffAST: recording that definition of `$name1` changed")
        newDef += (name1 -> after)
      }
    case (before, after) =>
      if (before != after) {
        ctx.echo(s"DiffAST: unhandled cases do not match: $before != $after")
        used = false
        return
      }
  }
}
