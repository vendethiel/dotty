package dotty.tools.dotc
package transform

import core._
import Names._
import dotty.tools.dotc.transform.TreeTransforms._
import ast.Trees._
import Phases._
import util.Positions._
import Flags._
import Types._
import Constants.Constant
import Contexts.Context
import Symbols._
import SymDenotations._
import Decorators._
import dotty.tools.dotc.core.Annotations.ConcreteAnnotation
import dotty.tools.dotc.core.Denotations.SingleDenotation
import scala.collection.mutable
import DenotTransformers._
import typer.Checking
import Names.Name
import NameOps._
import StdNames._

class CheckCaptures extends Phase {

//class CheckCaptures extends MiniPhaseTransform { thisTransformer =>
  import ast.tpd._

  override val phaseName = "checkCaptures"

  override def run(implicit ctx: Context): Unit = {
    val unit = ctx.compilationUnit

    val acc = new Checker

    acc.traverse(unit.tpdTree)
  }

  class Checker extends TreeTraverser {
    override def traverse(tree: Tree)(implicit ctx: Context) = tree match {
      case Apply(fun, args) =>
        traverse(fun)

        // Assume our usage of the standard library is safe
        val alwaysAllowCaptures = fun.symbol.fullName.startsWith("scala.")

        args.foreach({
          case c @ closure(env, meth, _) if alwaysAllowCaptures =>
            // Do nothing.
          case arg =>
            traverse(arg)
        })
      case c @ Closure(env, meth, _) =>
        capturedTypes(env, meth).foreach(checkCapture(_, meth, c.pos))
      case _ =>
        traverseChildren(tree)
    }

    def capturedTypes(env: List[Tree], meth: Tree)(implicit ctx: Context): List[Type] = {
      val envTypes = env.map(_.tpe)
      if (!meth.symbol.is(JavaStatic))
        meth.symbol.enclosingClass.typeRef :: envTypes
      else
        envTypes
    }

    def checkCapture(capturedType: Type, meth: Tree, pos: Position)(implicit ctx: Context): Unit = {
      val bases = capturedType.baseClasses
      val selfBases = bases.flatMap(_.classInfo.givenSelfType.baseClasses)

      if (!meth.symbol.hasAnnotation(defn.AllowCapturesAnnot) &&
          (bases ++ selfBases).exists(_.hasAnnotation(defn.CheckCapturesAnnot))) {
        ctx.error(i"Closure not authorized to capture ${capturedType}", pos)
      }
    }
  }
}
