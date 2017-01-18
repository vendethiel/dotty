package dotty.tools.dotc
package transform

import core.Symbols._
import core.StdNames._
import ast.Trees._
import core.Types._
import dotty.tools.dotc.core.Contexts.Context
import dotty.tools.dotc.transform.TreeTransforms.{MiniPhaseTransform, TransformerInfo}

import dotty.tools.dotc.core.Definitions.MaxCaseClassTupleArity
import dotty.tools.dotc.core.Constants.Constant
import annotation.tailrec

/** TODOC OLIVIER
 */
class TupleRewrites extends MiniPhaseTransform {
  import dotty.tools.dotc.ast.tpd._

  def phaseName: String = "tupleRewrites"

  override def checkPostCondition(tree: Tree)(implicit ctx: Context): Unit =
    tree match {
      case Select(ident, _) if ident.symbol == defn.TupleConsSymbol =>
        assert(false, s"Some TupleCons where not rewritten in $tree!")
      case _ => ()
    }

  /** Rewrites `TupleCons(a, TupleCons(b, ..., TNit))` to implementation specific constructors.
   *
   *  Below `MaxCaseClassTupleArity`, they become `TupleImpl$i(a, b, ...)`.
   *  Above `MaxCaseClassTupleArity`, they become `TupleImplN(Array.apply(a, b, ...)`.
   *
   *  Note that because of bottom up traversal, the transformation of a tuple constructor of size `N`
   *  will go thought this transformation `N` times, thus generating `N` `TupleCons(a, opt)` where `opt`
   *  is the optimized transformation for previous arity.
   */
  override def transformApply(tree: Apply)(implicit ctx: Context, info: TransformerInfo): Tree = {
    // Matches a tree with shape `TupleCons.apply(head, tail)` where `tail` itself a tuple
    // with statically known lenght (TNil, TupleImpl1, TupleImpl2...). */
    object TupleApplies {
      def unapply(tree: Apply)(implicit ctx: Context): Option[List[Tree]] =
        tree match {
          case Apply(TypeApply(Select(ident, nme.apply), _), head :: tail :: Nil)
            if ident.symbol == defn.TupleConsSymbol =>
              tail match {
                case _
                  if tail.symbol == defn.TNilSymbol =>
                    Some(head :: Nil)
                case Typed(Apply(TypeApply(Select(tailIdent, nme.apply), _), args), _)
                  if defn.TupleImplSymbols contains tailIdent.symbol =>
                    Some(head :: args)
                case Typed(Apply(TypeApply(Select(tailIdent, nme.wrap), _), SeqLiteral(args, _) :: Nil), _)
                  if tailIdent.symbol == defn.TupleImplNSymbol =>
                    Some(head :: args)
                case _ => None
              }
          case _ => None
        }
    }

    tree match {
      case TupleApplies(args) =>
        val arity = args.length
        val newSelect =
          if (arity <= MaxCaseClassTupleArity)
            ref(defn.TupleImplType(arity).classSymbol.companionModule) // TupleImpl${arity}(args)
              .select(nme.apply)
              .appliedToTypes(args.map(_.tpe))
              .appliedToArgs(args)
          else {
            val TupleConsTypeExtractor(headType, tailType) = tree.tpe
            ref(defn.TupleImplNType.classSymbol.companionModule) // TupleImplN.wrap()
              .select(nme.wrap)
              .appliedToTypes(headType :: tailType :: Nil)
              .appliedTo(SeqLiteral(args, ref(defn.AnyType)))
          }
        Typed(newSelect, TypeTree(tree.tpe))
      case _ => tree
    }
  }

  /** Rewrites `t.tail.tail. ... .head` to implementation specific selectors.
   *
   *  Below `MaxCaseClassTupleArity`, they become direct `t._i` accesses.
   *  Above `MaxCaseClassTupleArity`, they become `t.underlying.apply(i)` accesses.
   */
  override def transformSelect(tree: Select)(implicit ctx: Context, info: TransformerInfo): Tree = {
    // Matches a tree with shape `inner.tail.tail ...` when inner derives from `TupleCon`,
    // returning the `inner` tree and the number of `.tail`s.
    object TupleTails {
      def unapply(tree: Select)(implicit ctx: Context): Option[(Tree, Int)] = unapply0(tree, 0)
      @tailrec
      def unapply0(tree: Tree, depth: Int)(implicit ctx: Context): Option[(Tree, Int)] =
        tree match {
          case Select(inner, nme.tail) =>
            unapply0(inner, depth + 1)
          case _ if tree.tpe.derivesFrom(defn.TupleConsType.symbol) =>
            Some((tree, depth))
          case _ => None
        }
    }

    // Extracts tuple arity from it's type.
    @tailrec
    def tupleTypeArity(t: Type, acc: Int = 0)(implicit ctx: Context): Int =
      t.baseArgTypes(defn.TupleConsType.symbol) match {
        case _ :: y :: Nil => tupleTypeArity(y, acc + 1) // TupleCons[H, T <: Tuple]
        case _ => acc                                    // TNil
      }

    tree match {
      case Select(TupleTails(inner, tails), nme.head) =>
        val arity = tupleTypeArity(inner.tpe)
        val newSelect =
          if (arity <= MaxCaseClassTupleArity)
            inner // .asInstanceOf[TupleImpl$arity[_, _]]._${tail + 1}s.asInstanceOf[${tree.tpe}]
              .asInstance(defn.TupleImplType(arity))
              .select(nme.productAccessorName(tails + 1)) // t(0) <=> t._1, thus the +1
              .asInstance(tree.tpe)
          else
            inner // .asInstanceOf[TupleImplN[_, _]].underlying(${tails}).asInstanceOf[${tree.tpe}]
              .asInstance(defn.TupleImplNType)
              .select(nme.underlying)
              .select(nme.apply)
              .appliedTo(Literal(Constant(tails)))
              .asInstance(tree.tpe)
        Typed(newSelect, TypeTree(tree.tpe))
      case _ => tree
    }
  }

  /** Rewrites `TupleCons.unapply(a, TupleCons.unapply(b, ..., TNit))` to implementation specific extractors.
   *
   *  Below `MaxCaseClassTupleArity`, they become `TupleImpl$i.unapply(a, b, ...)`.
   *  Above `MaxCaseClassTupleArity`, they become `TupleUnapplySeq.unapply(a, b, ...)`.
   *
   *  Similarly to `transformApply`, size `N` extractors will pass `N` times thought this transformation.
   */
  override def transformUnApply(tree: UnApply)(implicit ctx: Context, info: TransformerInfo): Tree = {
    // Matches a tree with shape `TupleCons.unapply(head, tail)` where `tail` itself a tuple
    // with statically known lenght (`TNil`, `TupleImpl1`, `TupleImpl2`...).
    object TupleUnapplies {
      def unapply(tree: UnApply)(implicit ctx: Context): Option[List[Tree]] =
        tree match {
          case UnApply(TypeApply(Select(selectIndent, nme.unapply), _), Nil, firstPattern :: secondPattern :: Nil)
            if selectIndent.symbol == defn.TupleConsSymbol =>
              secondPattern match {
                case _
                  if secondPattern.symbol == defn.TNilSymbol =>
                    Some(List(firstPattern))
                case UnApply(TypeApply(Select(ident, nme.unapply), _), Nil, patterns)
                  if defn.TupleImplSymbols contains ident.symbol =>
                    Some(firstPattern :: patterns)
                case UnApply(TypeApply(Select(ident, nme.unapplySeq), _), Nil, patterns)
                  if ident.symbol == defn.TupleUnapplySeqSymbol  =>
                    Some(firstPattern :: patterns)
                case _ => None
              }
          case _ => None
        }
    }

    tree match {
      case TupleUnapplies(patterns) => transformUnApplyPatterns(tree, patterns)
      case _ => tree
    }
  }

  /** Same then `transformUnApply` for unapply wrapped in Typed trees. */
  override def transformTyped(tree: Typed)(implicit ctx: Context, info: TransformerInfo): Tree = {
    object TypedTupleUnapplies {
      def unapply(tree: Typed)(implicit ctx: Context): Option[List[Tree]] =
        tree match {
          case Typed(UnApply(TypeApply(Select(selectIndent, nme.unapply), _), Nil, firstPattern :: secondPattern :: Nil), _)
            if selectIndent.symbol == defn.TupleConsSymbol =>
              secondPattern match {
                case _
                  if secondPattern.symbol == defn.TNilSymbol =>
                    Some(List(firstPattern))
                case Typed(UnApply(TypeApply(Select(ident, nme.unapply), _), Nil, patterns), _)
                  if defn.TupleImplSymbols contains ident.symbol =>
                    Some(firstPattern :: patterns)
                case Typed(UnApply(TypeApply(Select(ident, nme.unapplySeq), _), Nil, patterns), _)
                  if ident.symbol == defn.TupleUnapplySeqSymbol  =>
                    Some(firstPattern :: patterns)
                case _ => None
              }
          case _ => None
        }
    }

    tree match {
      case TypedTupleUnapplies(patterns) =>
        val unapply = transformUnApplyPatterns(tree, patterns)
        Typed(unapply, TypeTree(unapply.tpe))
      case _ => tree
    }
  }

  // Create an `UnApply` tree from a list of patters, used in both transformUnApply and transformTyped.
  private def transformUnApplyPatterns(tree: Tree, patterns: List[Tree])(implicit ctx: Context): UnApply = {

    val arity = patterns.length
    if (arity <= MaxCaseClassTupleArity) {
      // TupleImpl${arity}.unapply(patterns)
      val baseType = defn.TupleImplType(arity)
      val patternTypes = patterns.map(_.tpe.widen)
      val refinedType = RefinedType.makeFullyDefined(baseType, patternTypes.map(t => TypeAlias(t, 0)))
      val newCall =
        ref(baseType.classSymbol.companionModule)
          .select(nme.unapply)
          .appliedToTypes(patternTypes)
      UnApply(fun = newCall, implicits = Nil, patterns = patterns, proto = refinedType)
    } else {
      // TupleUnapplySeq.unapplySeq(patterns)
      val TupleConsTypeExtractor(headType, tailType) = tree.tpe
      val newCall =
        ref(defn.TupleUnapplySeqType.classSymbol.companionModule)
          .select(nme.unapplySeq)
          .appliedToTypes(headType :: tailType :: Nil)
      UnApply(fun = newCall, implicits = Nil, patterns = patterns, proto = tree.tpe)
    }
  }

  // Extracts `(A, B)` from `TupleCons[A, B]`.
  private object TupleConsTypeExtractor {
    def unapply(tree: Type): Option[(Type, Type)] = tree match {
      case RefinedType(RefinedType(_, _, TypeAlias(headType)), _, TypeAlias(tailType)) => Some((headType, tailType))
      case AnnotatedType(t, _) => unapply(t)
      case _ => None
    }
  }
}
