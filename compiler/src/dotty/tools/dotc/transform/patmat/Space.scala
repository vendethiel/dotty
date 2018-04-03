package dotty.tools.dotc
package transform
package patmat

import core._
import Types._
import Contexts._
import Flags._
import ast._
import Trees._
import Decorators._
import Symbols._
import StdNames._
import NameOps._
import Constants._
import typer._
import Applications._
import Inferencing._
import ProtoTypes._
import transform.SymUtils._
import reporting.diagnostic.messages._
import config.Printers.{exhaustivity => debug}

/** Space logic for checking exhaustivity and unreachability of pattern matching
 *
 *  Space can be thought of as a set of possible values. A type or a pattern
 *  both refer to spaces. The space of a type is the values that inhabit the
 *  type. The space of a pattern is the values that can be covered by the
 *  pattern.
 *
 *  Space is recursively defined as follows:
 *
 *      1. `Empty` is a space
 *      2. For a type T, `Typ(T)` is a space
 *      3. A union of spaces `S1 | S2 | ...` is a space
 *      4. `Prod(S1, S2, ..., Sn)` is a product space.
 *
 *  For the problem of exhaustivity check, its formulation in terms of space is as follows:
 *
 *      Is the space Typ(T) a subspace of the union of space covered by all the patterns?
 *
 *  The problem of unreachable patterns can be formulated as follows:
 *
 *      Is the space covered by a pattern a subspace of the space covered by previous patterns?
 *
 *  Assumption:
 *    (1) One case class cannot be inherited directly or indirectly by another
 *        case class.
 *    (2) Inheritance of a case class cannot be well handled by the algorithm.
 *
 */


/** space definition */
sealed trait Space

/** Empty space */
case object Empty extends Space

/** Space representing the set of all values of a type
 *
 * @param tp: the type this space represents
 * @param decomposed: does the space result from decomposition? Used for pretty print
 *
 */
case class Typ(tp: Type, decomposed: Boolean) extends Space

/** Space representing an extractor pattern */
case class Prod(tp: Type, unappTp: Type, unappSym: Symbol, params: List[Space], full: Boolean) extends Space

/** Union of spaces */
case class Or(spaces: List[Space]) extends Space

/** abstract space logic */
trait SpaceLogic {
  /** Is `tp1` a subtype of `tp2`? */
  def isSubType(tp1: Type, tp2: Type): Boolean

  /** Is `tp1` the same type as `tp2`? */
  def isEqualType(tp1: Type, tp2: Type): Boolean

  /** Return a space containing the values of both types.
   *
   * The types should be atomic (non-decomposable) and unrelated (neither
   * should be a subtype of the other).
   */
  def intersectUnrelatedAtomicTypes(tp1: Type, tp2: Type): Space

  /** Is the type `tp` decomposable? i.e. all values of the type can be covered
   *  by its decomposed types.
   *
   * Abstract sealed class, OrType, Boolean and Java enums can be decomposed.
   */
  def canDecompose(tp: Type): Boolean

  /** Return term parameter types of the extractor `unapp` */
  def signature(unapp: Type, unappSym: Symbol, argLen: Int): List[Type]

  /** Get components of decomposable types */
  def decompose(tp: Type): List[Space]

  /** Display space in string format */
  def show(sp: Space): String

  /** Simplify space using the laws, there's no nested union after simplify
   *
   *  @param aggressive if true and OR space has less than 5 components, `simplify` will
   *                    collapse `sp1 | sp2` to `sp1` if `sp2` is a subspace of `sp1`.
   *
   *                    This reduces noise in counterexamples.
   */
  def simplify(space: Space, aggressive: Boolean = false): Space = space match {
    case Prod(tp, fun, sym, spaces, full) =>
      val sp = Prod(tp, fun, sym, spaces.map(simplify(_)), full)
      if (sp.params.contains(Empty)) Empty
      else sp
    case Or(spaces) =>
      val set = spaces.map(simplify(_)).flatMap {
        case Or(ss) => ss
        case s => Seq(s)
      } filter (_ != Empty)

      if (set.isEmpty) Empty
      else if (set.size == 1) set.toList(0)
      else if (aggressive && spaces.size < 5) {
        val res = set.map(sp => (sp, set.filter(_ ne sp))).find {
          case (sp, sps) =>
            isSubspace(sp, Or(sps))
        }
        if (res.isEmpty) Or(set)
        else simplify(Or(res.get._2), aggressive)
      }
      else Or(set)
    case Typ(tp, _) =>
      if (canDecompose(tp) && decompose(tp).isEmpty) Empty
      else space
    case _ => space
  }

  /** Flatten space to get rid of `Or` for pretty print */
  def flatten(space: Space): List[Space] = space match {
    case Prod(tp, fun, sym, spaces, full) =>
      spaces.map(flatten) match {
        case Nil => Prod(tp, fun, sym, Nil, full) :: Nil
        case ss  =>
          ss.foldLeft(List[Prod]()) { (acc, flat) =>
            if (acc.isEmpty) flat.map(s => Prod(tp, fun, sym, s :: Nil, full))
            else for (Prod(tp, fun, sym, ss, full) <- acc; s <- flat) yield Prod(tp, fun, sym, ss :+ s, full)
          }
      }
    case Or(spaces) =>
      spaces.flatMap(flatten _)
    case _ => List(space)
  }

  /** Is `a` a subspace of `b`? Equivalent to `a - b == Empty`, but faster */
  def isSubspace(a: Space, b: Space): Boolean = {
    def tryDecompose1(tp: Type) = canDecompose(tp) && isSubspace(Or(decompose(tp)), b)
    def tryDecompose2(tp: Type) = canDecompose(tp) && isSubspace(a, Or(decompose(tp)))

    val res = (simplify(a), b) match {
      case (Empty, _) => true
      case (_, Empty) => false
      case (Or(ss), _) =>
        ss.forall(isSubspace(_, b))
      case (Typ(tp1, _), Typ(tp2, _)) =>
        isSubType(tp1, tp2)
      case (Typ(tp1, _), Or(ss)) =>  // optimization: don't go to subtraction too early
        ss.exists(isSubspace(a, _)) || tryDecompose1(tp1)
      case (_, Or(_)) =>
        simplify(minus(a, b)) == Empty
      case (Prod(tp1, _, _, _, _), Typ(tp2, _)) =>
        isSubType(tp1, tp2)
      case (Typ(tp1, _), Prod(tp2, fun, sym, ss, full)) =>
        // approximation: a type can never be fully matched by a partial extractor
        full && isSubType(tp1, tp2) && isSubspace(Prod(tp2, fun, sym, signature(fun, sym, ss.length).map(Typ(_, false)), full), b)
      case (Prod(_, fun1, sym1, ss1, _), Prod(_, fun2, sym2, ss2, _)) =>
        sym1 == sym2 && isEqualType(fun1, fun2) && ss1.zip(ss2).forall((isSubspace _).tupled)
    }

    debug.println(s"${show(a)} < ${show(b)} = $res")

    res
  }

  /** Intersection of two spaces  */
  def intersect(a: Space, b: Space): Space = {
    def tryDecompose1(tp: Type) = intersect(Or(decompose(tp)), b)
    def tryDecompose2(tp: Type) = intersect(a, Or(decompose(tp)))

    val res: Space = (a, b) match {
      case (Empty, _) | (_, Empty) => Empty
      case (_, Or(ss)) => Or(ss.map(intersect(a, _)).filterConserve(_ ne Empty))
      case (Or(ss), _) => Or(ss.map(intersect(_, b)).filterConserve(_ ne Empty))
      case (Typ(tp1, _), Typ(tp2, _)) =>
        if (isSubType(tp1, tp2)) a
        else if (isSubType(tp2, tp1)) b
        else if (canDecompose(tp1)) tryDecompose1(tp1)
        else if (canDecompose(tp2)) tryDecompose2(tp2)
        else intersectUnrelatedAtomicTypes(tp1, tp2)
      case (Typ(tp1, _), Prod(tp2, fun, _, ss, true)) =>
        if (isSubType(tp2, tp1)) b
        else if (isSubType(tp1, tp2)) a // problematic corner case: inheriting a case class
        else if (canDecompose(tp1)) tryDecompose1(tp1)
        else Empty
      case (Typ(tp1, _), Prod(tp2, _, _, _, false)) =>
        if (isSubType(tp1, tp2) || isSubType(tp2, tp1)) b  // prefer extractor space for better approximation
        else if (canDecompose(tp1)) tryDecompose1(tp1)
        else Empty
      case (Prod(tp1, fun, _, ss, true), Typ(tp2, _)) =>
        if (isSubType(tp1, tp2)) a
        else if (isSubType(tp2, tp1)) a  // problematic corner case: inheriting a case class
        else if (canDecompose(tp2)) tryDecompose2(tp2)
        else Empty
      case (Prod(tp1, _, _, _, false), Typ(tp2, _)) =>
        if (isSubType(tp1, tp2) || isSubType(tp2, tp1)) a
        else if (canDecompose(tp2)) tryDecompose2(tp2)
        else Empty
      case (Prod(tp1, fun1, sym1, ss1, full), Prod(tp2, fun2, sym2, ss2, _)) =>
        if (sym1 != sym2 || !isEqualType(fun1, fun2)) Empty
        else if (ss1.zip(ss2).exists(p => simplify(intersect(p._1, p._2)) == Empty)) Empty
        else Prod(tp1, fun1, sym1, ss1.zip(ss2).map((intersect _).tupled), full)
    }

    debug.println(s"${show(a)} & ${show(b)} = ${show(res)}")

    res
  }

  /** The space of a not covered by b */
  def minus(a: Space, b: Space): Space = {
    def tryDecompose1(tp: Type) = minus(Or(decompose(tp)), b)
    def tryDecompose2(tp: Type) = minus(a, Or(decompose(tp)))

    val res = (a, b) match {
      case (Empty, _) => Empty
      case (_, Empty) => a
      case (Typ(tp1, _), Typ(tp2, _)) =>
        if (isSubType(tp1, tp2)) Empty
        else if (canDecompose(tp1)) tryDecompose1(tp1)
        else if (canDecompose(tp2)) tryDecompose2(tp2)
        else a
      case (Typ(tp1, _), Prod(tp2, fun, sym, ss, true)) =>
        // rationale: every instance of `tp1` is covered by `tp2(_)`
        if (isSubType(tp1, tp2)) minus(Prod(tp2, fun, sym, signature(fun, sym, ss.length).map(Typ(_, false)), true), b)
        else if (canDecompose(tp1)) tryDecompose1(tp1)
        else a
      case (_, Or(ss)) =>
        ss.foldLeft(a)(minus)
      case (Or(ss), _) =>
        Or(ss.map(minus(_, b)))
      case (Prod(tp1, fun, _, ss, true), Typ(tp2, _)) =>
        // uncovered corner case: tp2 :< tp1
        if (isSubType(tp1, tp2)) Empty
        else if (simplify(a) == Empty) Empty
        else if (canDecompose(tp2)) tryDecompose2(tp2)
        else a
      case (Prod(tp1, _, _, _, false), Typ(tp2, _)) =>
        if (isSubType(tp1, tp2)) Empty
        else a
      case (Typ(tp1, _), Prod(tp2, _, _, _, false)) =>
        a  // approximation
      case (Prod(tp1, fun1, sym1, ss1, full), Prod(tp2, fun2, sym2, ss2, _)) =>
        if (sym1 != sym2 || !isEqualType(fun1, fun2)) a
        else if (ss1.zip(ss2).exists(p => simplify(intersect(p._1, p._2)) == Empty)) a
        else if (ss1.zip(ss2).forall((isSubspace _).tupled)) Empty
        else
          // `(_, _, _) - (Some, None, _)` becomes `(None, _, _) | (_, Some, _) | (_, _, Empty)`
          Or(ss1.zip(ss2).map((minus _).tupled).zip(0 to ss2.length - 1).map {
            case (ri, i) => Prod(tp1, fun1, sym1, ss1.updated(i, ri), full)
          })

    }

    debug.println(s"${show(a)} - ${show(b)} = ${show(res)}")

    res
  }
}

/** Scala implementation of space logic */
class SpaceEngine(implicit ctx: Context) extends SpaceLogic {
  import tpd._

  private val scalaSomeClass       = ctx.requiredClass("scala.Some")
  private val scalaSeqFactoryClass = ctx.requiredClass("scala.collection.generic.SeqFactory")
  private val scalaListType        = ctx.requiredClassRef("scala.collection.immutable.List")
  private val scalaNilType         = ctx.requiredModuleRef("scala.collection.immutable.Nil")
  private val scalaConsType        = ctx.requiredClassRef("scala.collection.immutable.::")

  override def intersectUnrelatedAtomicTypes(tp1: Type, tp2: Type) = {
    val and = AndType(tp1, tp2)
    // Precondition: !(tp1 <:< tp2) && !(tp2 <:< tp1)
    // Then, no leaf of the and-type tree `and` is a subtype of `and`.
    val res = inhabited(and)

    debug.println(s"atomic intersection: ${and.show} = ${res}")

    if (res) Typ(and, true) else Empty
  }

  /** Whether the extractor is irrefutable */
  def irrefutable(unapp: Tree): Boolean = {
    // TODO: optionless patmat
    unapp.tpe.widen.finalResultType.isRef(scalaSomeClass) ||
      (unapp.symbol.is(Synthetic) && unapp.symbol.owner.linkedClass.is(Case)) ||
      productArity(unapp.tpe.widen.finalResultType) > 0
  }

  /** Return the space that represents the pattern `pat` */
  def project(pat: Tree): Space = pat match {
    case Literal(c) =>
      if (c.value.isInstanceOf[Symbol])
        Typ(c.value.asInstanceOf[Symbol].termRef, false)
      else
        Typ(ConstantType(c), false)
    case _: BackquotedIdent => Typ(pat.tpe, false)
    case Ident(_) | Select(_, _) =>
      Typ(pat.tpe.stripAnnots, false)
    case Alternative(trees) => Or(trees.map(project(_)))
    case Bind(_, pat) => project(pat)
    case SeqLiteral(pats, _) => projectSeq(pats)
    case UnApply(fun, _, pats) =>
      if (fun.symbol.name == nme.unapplySeq)
        if (fun.symbol.owner == scalaSeqFactoryClass)
          projectSeq(pats)
        else
          Prod(erase(pat.tpe.stripAnnots), fun.tpe, fun.symbol, projectSeq(pats) :: Nil, irrefutable(fun))
      else
        Prod(erase(pat.tpe.stripAnnots), fun.tpe, fun.symbol, pats.map(project), irrefutable(fun))
    case Typed(pat @ UnApply(_, _, _), _) => project(pat)
    case Typed(expr, tpt) =>
      Typ(erase(expr.tpe.stripAnnots), true)
    case _ =>
      debug.println(s"unknown pattern: $pat")
      Empty
  }

  /* Erase a type binding according to erasure semantics in pattern matching */
  def erase(tp: Type): Type = tp match {
    case tp @ AppliedType(tycon, args) =>
      if (tycon.isRef(defn.ArrayClass)) tp.derivedAppliedType(tycon, args.map(erase))
      else tp.derivedAppliedType(tycon, args.map(t => WildcardType))
    case OrType(tp1, tp2) =>
      OrType(erase(tp1), erase(tp2))
    case AndType(tp1, tp2) =>
      AndType(erase(tp1), erase(tp2))
    case tp: RefinedType =>
      tp.derivedRefinedType(erase(tp.parent), tp.refinedName, WildcardType)
    case _ => tp
  }

  /** Space of the pattern: unapplySeq(a, b, c: _*)
   */
  def projectSeq(pats: List[Tree]): Space = {
    if (pats.isEmpty) return Typ(scalaNilType, false)

    val (items, zero) = if (pats.last.tpe.isRepeatedParam)
      (pats.init, Typ(scalaListType.appliedTo(pats.last.tpe.argTypes.head), false))
    else
      (pats, Typ(scalaNilType, false))

    items.foldRight[Space](zero) { (pat, acc) =>
      val consTp = scalaConsType.appliedTo(pats.head.tpe.widen)
      val unapplySym = consTp.classSymbol.linkedClass.info.member(nme.unapply).symbol
      val unapplyTp = unapplySym.info.appliedTo(pats.head.tpe.widen)
      Prod(consTp, unapplyTp, unapplySym, project(pat) :: acc :: Nil, true)
    }
  }

  /** Is `tp1` a subtype of `tp2`?  */
  def isSubType(tp1: Type, tp2: Type): Boolean = {
    val res = tp1 <:< tp2
    debug.println(s"${tp1.show} <:< ${tp2.show} = $res")
    res
  }

  def isEqualType(tp1: Type, tp2: Type): Boolean = tp1 =:= tp2

  /** Parameter types of the case class type `tp`. Adapted from `unapplyPlan` in patternMatcher  */
  def signature(unapp: Type, unappSym: Symbol, argLen: Int): List[Type] = {
    def caseClass = unappSym.owner.linkedClass

    lazy val caseAccessors = caseClass.caseAccessors.filter(_.is(Method))

    def isSyntheticScala2Unapply(sym: Symbol) =
      sym.is(SyntheticCase) && sym.owner.is(Scala2x)

    val mt @ MethodType(_) = unapp.widen

    // Case unapply:
    // 1. return types of constructor fields if the extractor is synthesized for Scala2 case classes & length match
    // 2. return Nil if unapply returns Boolean  (boolean pattern)
    // 3. return product selector types if unapply returns a product type (product pattern)
    // 4. return product selectors of `T` where `def get: T` is a member of the return type of unapply & length match (named-based pattern)
    // 5. otherwise, return `T` where `def get: T` is a member of the return type of unapply
    //
    // Case unapplySeq:
    // 1. return the type `List[T]` where `T` is the element type of the unapplySeq return type `Seq[T]`

    val sig =
      if (isSyntheticScala2Unapply(unappSym) && caseAccessors.length == argLen)
        caseAccessors.map(_.info.asSeenFrom(mt.paramInfos.head, caseClass).widenExpr)
      else if (mt.finalResultType.isRef(defn.BooleanClass))
        List()
      else {
        val isUnapplySeq = unappSym.name == nme.unapplySeq
        if (isProductMatch(mt.finalResultType, argLen) && !isUnapplySeq) {
          productSelectors(mt.finalResultType).take(argLen)
            .map(_.info.asSeenFrom(mt.finalResultType, mt.resultType.classSymbol).widenExpr)
        }
        else {
          val resTp = mt.finalResultType.select(nme.get).finalResultType.widen
          if (isUnapplySeq) scalaListType.appliedTo(resTp.argTypes.head) :: Nil
          else if (argLen == 0) Nil
          else if (isProductMatch(resTp, argLen))
            productSelectors(resTp).map(_.info.asSeenFrom(resTp, resTp.classSymbol).widenExpr)
          else resTp :: Nil
        }
      }

    debug.println(s"signature of ${unappSym.showFullName} ----> ${sig.map(_.show).mkString(", ")}")

    sig.map(_.annotatedToRepeated)
  }

  /** Decompose a type into subspaces -- assume the type can be decomposed */
  def decompose(tp: Type): List[Space] = {
    val children = tp.classSymbol.children

    debug.println(s"candidates for ${tp.show} : [${children.map(_.show).mkString(", ")}]")

    tp.dealias match {
      case AndType(tp1, tp2) =>
        intersect(Typ(tp1, false), Typ(tp2, false)) match {
          case Or(spaces) => spaces
          case Empty => Nil
          case space => List(space)
        }
      case OrType(tp1, tp2) => List(Typ(tp1, true), Typ(tp2, true))
      case tp if tp.isRef(defn.BooleanClass) =>
        List(
          Typ(ConstantType(Constant(true)), true),
          Typ(ConstantType(Constant(false)), true)
        )
      case tp if tp.isRef(defn.UnitClass) =>
        Typ(ConstantType(Constant(())), true) :: Nil
      case tp if tp.classSymbol.is(Enum) =>
        children.map(sym => Typ(sym.termRef, true))
      case tp =>
        val parts = children.map { sym =>
          if (sym.is(ModuleClass))
            refine(tp, sym.sourceModule)
          else
            refine(tp, sym)
        } filter(_.exists)

        debug.println(s"${tp.show} decomposes to [${parts.map(_.show).mkString(", ")}]")

        parts.map(Typ(_, true))
    }
  }

  /** Refine child based on parent
   *
   *  In child class definition, we have:
   *
   *      class Child[Ts] extends path.Parent[Us] with Es
   *      object Child extends path.Parent[Us] with Es
   *      val child = new path.Parent[Us] with Es           // enum values
   *
   *  Given a parent type `parent` and a child symbol `child`, we infer the prefix
   *  and type parameters for the child:
   *
   *      prefix.child[Vs] <:< parent
   *
   *  where `Vs` are fresh type variables and `prefix` is the symbol prefix with all
   *  non-module and non-package `ThisType` replaced by fresh type variables.
   *
   *  If the subtyping is true, the instantiated type `p.child[Vs]` is
   *  returned. Otherwise, `NoType` is returned.
   *
   */
  def refine(parent: Type, child: Symbol): Type = {
    if (child.isTerm && child.is(Case, butNot = Module)) return child.termRef // enum vals always match

    val childTp = if (child.isTerm) child.termRef else child.typeRef

    val resTp = instantiate(childTp, parent)(ctx.fresh.setNewTyperState())

    if (!resTp.exists || !inhabited(resTp)) {
      debug.println(s"[refine] unqualified child ousted: ${childTp.show} !< ${parent.show}")
      NoType
    }
    else {
      debug.println(s"$child instantiated ------> $resTp")
      resTp.dealias
    }
  }

  /** Can this type be inhabited by a value?
   *
   *  Check is based on the following facts:
   *
   *  - single inheritance of classes
   *  - final class cannot be extended
   *  - intersection of a singleton type with another irrelevant type  (patmat/i3574.scala)
   *
   */
  def inhabited(tp: Type)(implicit ctx: Context): Boolean = {
    // convert top-level type shape into "conjunctive normal form"
    def cnf(tp: Type): Type = tp match {
      case AndType(OrType(l, r), tp)      =>
        OrType(cnf(AndType(l, tp)), cnf(AndType(r, tp)))
      case AndType(tp, o: OrType)         =>
        cnf(AndType(o, tp))
      case AndType(l, r)                  =>
        val l1 = cnf(l)
        val r1 = cnf(r)
        if (l1.ne(l) || r1.ne(r)) cnf(AndType(l1, r1))
        else AndType(l1, r1)
      case OrType(l, r)                   =>
        OrType(cnf(l), cnf(r))
      case tp @ RefinedType(OrType(tp1, tp2), _, _)  =>
        OrType(
          cnf(tp.derivedRefinedType(tp1, refinedName = tp.refinedName, refinedInfo = tp.refinedInfo)),
          cnf(tp.derivedRefinedType(tp2, refinedName = tp.refinedName, refinedInfo = tp.refinedInfo))
        )
      case tp: RefinedType                =>
        val parent1 = cnf(tp.parent)
        val tp1 = tp.derivedRefinedType(parent1, refinedName = tp.refinedName, refinedInfo = tp.refinedInfo)

        if (parent1.ne(tp.parent)) cnf(tp1) else tp1
      case tp: TypeAlias                  =>
        cnf(tp.alias)
      case _                              =>
        tp
    }

    def isSingleton(tp: Type): Boolean = tp.dealias match {
      case AndType(l, r)  => isSingleton(l) || isSingleton(r)
      case OrType(l, r)   => isSingleton(l) && isSingleton(r)
      case tp             => tp.isSingleton
    }

    def superType(tp: Type): Type = tp match {
      case tp: TypeProxy     => tp.superType
      case OrType(tp1, tp2)  => OrType(superType(tp1), superType(tp2))
      case AndType(tp1, tp2) => AndType(superType(tp1), superType(tp2))
      case _                 => tp
    }

    def recur(tp: Type): Boolean = tp.dealias match {
      case AndType(tp1, tp2) =>
        recur(tp1) && recur(tp2) && {
          val bases1 = tp1.widenDealias.classSymbols
          val bases2 = tp2.widenDealias.classSymbols

          debug.println(s"bases of ${tp1.show}: " + bases1)
          debug.println(s"bases of ${tp2.show}: " + bases2)
          debug.println(s"${tp1.show} <:< ${tp2.show} : " +  (tp1 <:< tp2))
          debug.println(s"${tp2.show} <:< ${tp1.show} : " +  (tp2 <:< tp1))

          val noClassConflict =
            bases1.forall(sym1 => sym1.is(Trait) || bases2.forall(sym2 => sym2.is(Trait) || sym1.isSubClass(sym2))) ||
            bases1.forall(sym1 => sym1.is(Trait) || bases2.forall(sym2 => sym2.is(Trait) || sym2.isSubClass(sym1)))

          debug.println(s"class conflict for ${tp.show}? " + !noClassConflict)

          noClassConflict &&
            (!isSingleton(tp1) || tp1 <:< tp2) &&
            (!isSingleton(tp2) || tp2 <:< tp1) &&
            (!bases1.exists(_ is Final) || tp1 <:< superType(tp2)) &&
            (!bases2.exists(_ is Final) || tp2 <:< superType(tp1))
        }
      case OrType(tp1, tp2) =>
        recur(tp1) || recur(tp2)
      case tp: RefinedType =>
        recur(tp.parent)
      case tp: TypeRef =>
        recur(tp.prefix) && !(tp.classSymbol.is(AbstractFinal))
      case _ =>
        true
    }

    val res = recur(cnf(tp))

    debug.println(s"${tp.show} inhabited?  " + res)

    res
  }

  /** Instantiate type `tp1` to be a subtype of `tp2`
   *
   *  Return the instantiated type if type parameters and this type
   *  in `tp1` can be instantiated such that `tp1 <:< tp2`.
   *
   *  Otherwise, return NoType.
   *
   */
  def instantiate(tp1: NamedType, tp2: Type)(implicit ctx: Context): Type = {
    // expose abstract type references to their bounds or tvars according to variance
    class AbstractTypeMap(maximize: Boolean)(implicit ctx: Context) extends TypeMap {
      def expose(lo: Type, hi: Type): Type =
        if (variance == 0)
          newTypeVar(TypeBounds(lo, hi))
        else if (variance == 1)
          if (maximize) hi else lo
        else
          if (maximize) lo else hi

      def apply(tp: Type): Type = tp match {
        case tp: TypeRef if tp.underlying.isInstanceOf[TypeBounds] =>
          val lo = this(tp.info.loBound)
          val hi = this(tp.info.hiBound)
          // See tests/patmat/gadt.scala  tests/patmat/exhausting.scala  tests/patmat/t9657.scala
          val exposed = expose(lo, hi)
          debug.println(s"$tp exposed to =====> $exposed")
          exposed

        case AppliedType(tycon: TypeRef, args) if tycon.underlying.isInstanceOf[TypeBounds] =>
          val args2 = args.map(this)
          val lo = this(tycon.info.loBound).applyIfParameterized(args2)
          val hi = this(tycon.info.hiBound).applyIfParameterized(args2)
          val exposed = expose(lo, hi)
          debug.println(s"$tp exposed to =====> $exposed")
          exposed

        case _ =>
          mapOver(tp)
      }
    }

    def minTypeMap(implicit ctx: Context) = new AbstractTypeMap(maximize = false)
    def maxTypeMap(implicit ctx: Context) = new AbstractTypeMap(maximize = true)

    // Fix subtype checking for child instantiation,
    // such that `Foo(Test.this.foo) <:< Foo(Foo.this)`
    // See tests/patmat/i3938.scala
    def removeThisType(implicit ctx: Context) = new TypeMap {
      // is in tvarBounds? Don't create new tvars if true
      private var tvarBounds: Boolean = false
      def apply(tp: Type): Type = tp match {
        case ThisType(tref: TypeRef) if !tref.symbol.isStaticOwner =>
          if (tref.symbol.is(Module))
            TermRef(this(tref.prefix), tref.symbol.sourceModule)
          else if (tvarBounds)
            this(tref)
          else {
            tvarBounds = true
            newTypeVar(TypeBounds.upper(this(tref)))
          }
        case tp => mapOver(tp)
      }
    }

    // replace uninstantiated type vars with WildcardType, check tests/patmat/3333.scala
    def instUndetMap(implicit ctx: Context) = new TypeMap {
      def apply(t: Type): Type = t match {
        case tvar: TypeVar if !tvar.isInstantiated => WildcardType(tvar.origin.underlying.bounds)
        case _ => mapOver(t)
      }
    }

    val force = new ForceDegree.Value(
      tvar => !(ctx.typerState.constraint.entry(tvar.origin) eq tvar.origin.underlying),
      minimizeAll = false
    )

    val tvars = tp1.typeParams.map { tparam => newTypeVar(tparam.paramInfo.bounds) }
    val protoTp1 = removeThisType.apply(tp1).appliedTo(tvars)

    // If parent contains a reference to an abstract type, then we should
    // refine subtype checking to eliminate abstract types according to
    // variance. As this logic is only needed in exhaustivity check,
    // we manually patch subtyping check instead of changing TypeComparer.
    // See tests/patmat/i3645b.scala
    def parentQualify = tp1.widen.classSymbol.info.parents.exists { parent =>
      implicit val ictx = ctx.fresh.setNewTyperState()
      parent.argInfos.nonEmpty && minTypeMap.apply(parent) <:< maxTypeMap.apply(tp2)
    }

    if (protoTp1 <:< tp2) {
      if (isFullyDefined(protoTp1, force)) protoTp1
      else instUndetMap.apply(protoTp1)
    }
    else {
      val protoTp2 = maxTypeMap.apply(tp2)
      if (protoTp1 <:< protoTp2 || parentQualify) {
        if (isFullyDefined(AndType(protoTp1, protoTp2), force)) protoTp1
        else instUndetMap.apply(protoTp1)
      }
      else {
        debug.println(s"$protoTp1 <:< $protoTp2 = false")
        NoType
      }
    }
  }

  /** Abstract sealed types, or-types, Boolean and Java enums can be decomposed */
  def canDecompose(tp: Type): Boolean = {
    val dealiasedTp = tp.dealias
    val res =
      (tp.classSymbol.is(Sealed) &&
        tp.classSymbol.is(AbstractOrTrait) &&
        tp.classSymbol.children.nonEmpty ) ||
      dealiasedTp.isInstanceOf[OrType] ||
      (dealiasedTp.isInstanceOf[AndType] && {
        val and = dealiasedTp.asInstanceOf[AndType]
        canDecompose(and.tp1) || canDecompose(and.tp2)
      }) ||
      tp.isRef(defn.BooleanClass) ||
      tp.isRef(defn.UnitClass) ||
      tp.classSymbol.is(allOf(Enum, Sealed))  // Enum value doesn't have Sealed flag

    debug.println(s"decomposable: ${tp.show} = $res")

    res
  }

  /** Show friendly type name with current scope in mind
   *
   *  E.g.    C.this.B     -->  B     if current owner is C
   *          C.this.x.T   -->  x.T   if current owner is C
   *          X[T]         -->  X
   *          C            -->  C     if current owner is C !!!
   *
   */
  def showType(tp: Type): String = {
    val enclosingCls = ctx.owner.enclosingClass

    def isOmittable(sym: Symbol) =
      sym.isEffectiveRoot || sym.isAnonymousClass || sym.name.isReplWrapperName ||
        ctx.definitions.UnqualifiedOwnerTypes.exists(_.symbol == sym) ||
        sym.showFullName.startsWith("scala.") ||
        sym == enclosingCls || sym == enclosingCls.sourceModule

    def refinePrefix(tp: Type): String = tp match {
      case NoPrefix => ""
      case tp: NamedType if isOmittable(tp.symbol) => ""
      case tp: ThisType => refinePrefix(tp.tref)
      case tp: RefinedType => refinePrefix(tp.parent)
      case tp: NamedType => tp.name.show.stripSuffix("$")
      case tp: TypeVar => refinePrefix(tp.instanceOpt)
      case _ => tp.show
    }

    def refine(tp: Type): String = tp match {
      case tp: RefinedType => refine(tp.parent)
      case tp: AppliedType => refine(tp.typeConstructor)
      case tp: ThisType => refine(tp.tref)
      case tp: NamedType =>
        val pre = refinePrefix(tp.prefix)
        if (tp.name == tpnme.higherKinds) pre
        else if (pre.isEmpty) tp.name.show.stripSuffix("$")
        else pre + "." + tp.name.show.stripSuffix("$")
      case _ => tp.show.stripSuffix("$")
    }

    val text = tp.stripAnnots match {
      case tp: OrType => showType(tp.tp1) + " | " + showType(tp.tp2)
      case tp => refine(tp)
    }

    if (text.isEmpty) enclosingCls.show.stripSuffix("$")
    else text
  }

  /** Whether the counterexample is satisfiable. The space is flattened and non-empty. */
  def satisfiable(sp: Space): Boolean = {
    def impossible: Nothing = throw new AssertionError("`satisfiable` only accepts flattened space.")

    def genConstraint(space: Space): List[(Type, Type)] = space match {
      case Prod(tp, unappTp, unappSym, ss, _) =>
        val tps = signature(unappTp, unappSym, ss.length)
        ss.zip(tps).flatMap {
          case (sp : Prod, tp) => sp.tp -> tp :: genConstraint(sp)
          case (Typ(tp1, _), tp2) => tp1 -> tp2 :: Nil
          case _ => impossible
        }
      case Typ(_, _) => Nil
      case _ => impossible
    }

    def checkConstraint(constrs: List[(Type, Type)])(implicit ctx: Context): Boolean = {
      val tvarMap = collection.mutable.Map.empty[Symbol, TypeVar]
      val typeParamMap = new TypeMap() {
        override def apply(tp: Type): Type = tp match {
          case tref: TypeRef if tref.symbol.is(TypeParam) =>
            tvarMap.getOrElseUpdate(tref.symbol, newTypeVar(tref.underlying.bounds))
          case tp => mapOver(tp)
        }
      }

      constrs.forall { case (tp1, tp2) => typeParamMap(tp1) <:< typeParamMap(tp2) }
    }

    checkConstraint(genConstraint(sp))(ctx.fresh.setNewTyperState())
  }

  /** Display spaces */
  def show(s: Space): String = {
    def params(tp: Type): List[Type] = tp.classSymbol.primaryConstructor.info.firstParamTypes

    /** does the companion object of the given symbol have custom unapply */
    def hasCustomUnapply(sym: Symbol): Boolean = {
      val companion = sym.companionModule
      companion.findMember(nme.unapply, NoPrefix, excluded = Synthetic).exists ||
        companion.findMember(nme.unapplySeq, NoPrefix, excluded = Synthetic).exists
    }

    def doShow(s: Space, mergeList: Boolean = false): String = s match {
      case Empty => ""
      case Typ(c: ConstantType, _) => c.value.show
      case Typ(tp: TermRef, _) => tp.symbol.showName
      case Typ(tp, decomposed) =>
        val sym = tp.widen.classSymbol

        if (ctx.definitions.isTupleType(tp))
          params(tp).map(_ => "_").mkString("(", ", ", ")")
        else if (scalaListType.isRef(sym))
          if (mergeList) "_: _*" else "_: List"
        else if (scalaConsType.isRef(sym))
          if (mergeList) "_, _: _*"  else "List(_, _: _*)"
        else if (tp.classSymbol.is(CaseClass) && !hasCustomUnapply(tp.classSymbol))
        // use constructor syntax for case class
          showType(tp) + params(tp).map(_ => "_").mkString("(", ", ", ")")
        else if (decomposed) "_: " + showType(tp)
        else "_"
      case Prod(tp, fun, sym, params, _) =>
        if (ctx.definitions.isTupleType(tp))
          "(" + params.map(doShow(_)).mkString(", ") + ")"
        else if (tp.isRef(scalaConsType.symbol))
          if (mergeList) params.map(doShow(_, mergeList)).mkString(", ")
          else params.map(doShow(_, true)).filter(_ != "Nil").mkString("List(", ", ", ")")
        else
          showType(sym.owner.typeRef) + params.map(doShow(_)).mkString("(", ", ", ")")
      case Or(_) =>
        throw new Exception("incorrect flatten result " + s)
    }

    flatten(s).map(doShow(_, false)).distinct.mkString(", ")
  }

  private def exhaustivityCheckable(sel: Tree): Boolean = {
    // Possible to check everything, but be compatible with scalac by default
    def isCheckable(tp: Type): Boolean =
      !tp.hasAnnotation(defn.UncheckedAnnot) && {
        val tpw = tp.widen.dealias
        ctx.settings.YcheckAllPatmat.value ||
        tpw.typeSymbol.is(Sealed) ||
        tpw.isInstanceOf[OrType] ||
        (tpw.isInstanceOf[AndType] && {
          val and = tpw.asInstanceOf[AndType]
          isCheckable(and.tp1) || isCheckable(and.tp2)
        }) ||
        tpw.isRef(defn.BooleanClass) ||
        tpw.typeSymbol.is(Enum) ||
        canDecompose(tpw) ||
        (defn.isTupleType(tpw) && tpw.argInfos.exists(isCheckable(_)))
      }

    val res = isCheckable(sel.tpe)
    debug.println(s"exhaustivity checkable: ${sel.show} = $res")
    res
  }

  /** Whehter counter-examples should be further checked? True for GADTs. */
  def shouldCheckExamples(tp: Type): Boolean = {
    new TypeAccumulator[Boolean] {
      override def apply(b: Boolean, tp: Type): Boolean = tp match {
        case tref: TypeRef if tref.symbol.is(TypeParam) && variance != 1 => true
        case tp => b || foldOver(b, tp)
      }
    }.apply(false, tp)
  }

  def checkExhaustivity(_match: Match): Unit = {
    val Match(sel, cases) = _match
    val selTyp = sel.tpe.widen.dealias

    if (!exhaustivityCheckable(sel)) return

    val patternSpace = cases.map({ x =>
      val space = project(x.pat)
      debug.println(s"${x.pat.show} ====> ${show(space)}")
      space
    }).reduce((a, b) => Or(List(a, b)))

    val checkGADTSAT = shouldCheckExamples(selTyp)

    val uncovered =
      flatten(simplify(minus(Typ(selTyp, true), patternSpace), aggressive = true))
        .filter(s => s != Empty && (!checkGADTSAT || satisfiable(s)))

    if (uncovered.nonEmpty)
      ctx.warning(PatternMatchExhaustivity(show(Or(uncovered))), sel.pos)
  }

  private def redundancyCheckable(sel: Tree): Boolean =
    !sel.tpe.hasAnnotation(defn.UncheckedAnnot)

  def checkRedundancy(_match: Match): Unit = {
    val Match(sel, cases) = _match
    val selTyp = sel.tpe.widen.dealias

    if (!redundancyCheckable(sel)) return

    (0 until cases.length).foreach { i =>
      // in redundancy check, take guard as false in order to soundly approximate
      val prevs =
        if (i == 0)
          Empty
        else
          cases.take(i).map { x =>
            if (x.guard.isEmpty) project(x.pat)
            else Empty
          }.reduce((a, b) => Or(List(a, b)))

      val curr = project(cases(i).pat)

      debug.println(s"---------------reachable? ${show(curr)}")
      debug.println(s"prev: ${show(prevs)}")

      if (isSubspace(intersect(curr, Typ(selTyp, false)), prevs)) {
        ctx.warning(MatchCaseUnreachable(), cases(i).body.pos)
      }
    }
  }
}
