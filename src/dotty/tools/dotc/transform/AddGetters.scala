package dotty.tools.dotc
package transform

import core._
import DenotTransformers.SymTransformer
import Phases.Phase
import Contexts.Context
import SymDenotations.SymDenotation
import Types._
import ast.Trees._
import TreeTransforms._
import Flags._

class AddGetters extends MiniPhaseTransform with SymTransformer { thisTransformer =>
  import ast.tpd._

  override def phaseName = "addGetters"

  override def transformSym(d: SymDenotation)(implicit ctx: Context): SymDenotation =
    if (d.isTerm && d.owner.isClass && !d.is(Method) && !d.is(Private, butNot = NotJavaPrivate) && d.info.isValueType) {
      val stable = if (d.isStable) Stable else EmptyFlags
      d.copySymDenotation(
        initFlags = d.flags | stable | AccessorCreationFlags,
        info = ExprType(d.info))
    }
    else d

  override def treeTransformPhase = thisTransformer.next

  override def transformValDef(tree: ValDef)(implicit ctx: Context, info: TransformerInfo): Tree = {
    if (tree.symbol is Method) {
      val getter = tree.symbol.asTerm
      assert(getter is Accessor)
      val field = ctx.newSymbol(
          ctx.owner,
          getter.name,
          getter.flags &~ AccessorCreationFlags &~ NotJavaPrivate | Private,
          getter.info.resultType).enteredAfter(thisTransformer)
      val fieldDef = tree.withType(field.valRef)
      val accessorDef = DefDef(getter, ref(field))
      Thicket(fieldDef, accessorDef)
    }
    else tree
  }
}