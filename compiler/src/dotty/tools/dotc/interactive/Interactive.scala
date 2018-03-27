package dotty.tools
package dotc
package interactive

import scala.annotation.tailrec
import scala.collection._

import ast.{NavigateAST, Trees, tpd, untpd}
import core._, core.Decorators.{sourcePos => _, _}
import Contexts._, Flags._, Names._, NameOps._, Symbols._, SymDenotations._, Trees._, Types._
import util.Positions._, util.SourcePosition
import core.Denotations.SingleDenotation
import NameKinds.SimpleNameKind
import config.Printers.interactiv
import StdNames.nme

/** High-level API to get information out of typed trees, designed to be used by IDEs.
 *
 *  @see `InteractiveDriver` to get typed trees from code.
 */
object Interactive {
  import ast.tpd._

  object Include { // should be an enum, really.
    type Set = Int
    val overridden = 1 // include trees whose symbol is overridden by `sym`
    val overriding = 2 // include trees whose symbol overrides `sym` (but for performance only in same source file)
    val references = 4 // include references and not just definitions
  }

  /** Does this tree define a symbol ? */
  def isDefinition(tree: Tree) =
    tree.isInstanceOf[DefTree with NameTree]

  /** The type of the closest enclosing tree with a type containing position `pos`. */
  def enclosingType(trees: List[SourceTree], pos: SourcePosition)(implicit ctx: Context): Type = {
    val path = pathTo(trees, pos)
    if (path.isEmpty) NoType
    else path.head.tpe
  }

  /** The closest enclosing tree with a symbol containing position `pos`.
   */
  def enclosingTree(trees: List[SourceTree], pos: SourcePosition)(implicit ctx: Context): Tree =
    pathTo(trees, pos).dropWhile(!_.symbol.exists).headOption.getOrElse(tpd.EmptyTree)

  /** The source symbol of the closest enclosing tree with a symbol containing position `pos`.
   *
   *  @see sourceSymbol
   */
  def enclosingSourceSymbol(trees: List[SourceTree], pos: SourcePosition)(implicit ctx: Context): Symbol =
    sourceSymbol(enclosingTree(trees, pos).symbol)

  /** A symbol related to `sym` that is defined in source code.
   *
   *  @see enclosingSourceSymbol
   */
  @tailrec def sourceSymbol(sym: Symbol)(implicit ctx: Context): Symbol =
    if (!sym.exists)
      sym
    else if (sym.is(ModuleVal))
      sourceSymbol(sym.moduleClass) // The module val always has a zero-extent position
    else if (sym.is(Synthetic)) {
      val linked = sym.linkedClass
      if (linked.exists && !linked.is(Synthetic))
        linked
      else
        sourceSymbol(sym.owner)
    }
    else if (sym.isPrimaryConstructor)
      sourceSymbol(sym.owner)
    else sym

  /** Check if `tree` matches `sym`.
   *  This is the case if the symbol defined by `tree` equals `sym`,
   *  or the source symbol of tree equals sym,
   *  or `include` is `overridden`, and `tree` is overridden by `sym`,
   *  or `include` is `overriding`, and `tree` overrides `sym`.
   */
  def matchSymbol(tree: Tree, sym: Symbol, include: Include.Set)(implicit ctx: Context): Boolean = {

    def overrides(sym1: Symbol, sym2: Symbol) =
      sym1.owner.derivesFrom(sym2.owner) && sym1.overriddenSymbol(sym2.owner.asClass) == sym2

    (  sym == tree.symbol
    || sym.exists && sym == sourceSymbol(tree.symbol)
    || include != 0 && sym.name == tree.symbol.name && sym.maybeOwner != tree.symbol.maybeOwner
       && (  (include & Include.overridden) != 0 && overrides(sym, tree.symbol)
          || (include & Include.overriding) != 0 && overrides(tree.symbol, sym)
          )
    )
  }

  private def safely[T](op: => List[T]): List[T] =
    try op catch { case ex: TypeError => Nil }

  /** Get possible completions from tree at `pos`
   *
   *  @return offset and list of symbols for possible completions
   */
  // deprecated
  // FIXME: Remove this method
  def completions(trees: List[SourceTree], pos: SourcePosition)(implicit ctx: Context): (Int, List[Symbol]) = {
    val path = pathTo(trees, pos)
    val boundary = enclosingDefinitionInPath(path).symbol

    // FIXME: Get all declarations available in the current scope, not just
    // those from the enclosing class
    def scopeCompletions: List[Symbol] =
      boundary.enclosingClass match {
        case csym: ClassSymbol =>
          val classRef = csym.classInfo.appliedRef
          completions(classRef, boundary)
        case _ =>
          Nil
      }

    path.headOption.map {
      case sel @ Select(qual, name) =>
        // When completing "`a.foo`, return the members of `a`
        (sel.pos.point, completions(qual.tpe, boundary))
      case id @ Ident(name) =>
        (id.pos.point, scopeCompletions)
      case _ =>
        (0, scopeCompletions)
    }
    .getOrElse((0, Nil))
  }

  /** Get possible completions from tree at `pos`
   *
   *  @return offset and list of symbols for possible completions
   */
  def completions(pos: SourcePosition)(implicit ctx: Context): (Int, List[Symbol]) = {
    val path = pathTo(ctx.compilationUnit.tpdTree, pos.pos)
    computeCompletions(pos, path)(contextOfPath(path))
  }

  private def computeCompletions(pos: SourcePosition, path: List[Tree])(implicit ctx: Context): (Int, List[Symbol]) = {
    val completions = Scopes.newScope.openForMutations

    val (completionPos, prefix, termOnly, typeOnly) = path match {
      case (ref: RefTree) :: _ =>
        if (ref.name == nme.ERROR)
          (ref.pos.point, "", false, false)
        else
          (ref.pos.point,
           ref.name.toString.take(pos.pos.point - ref.pos.point),
           ref.name.isTermName,
           ref.name.isTypeName)
      case _ =>
        (0, "", false, false)
    }

    /** Include in completion sets only symbols that
     *   1. start with given name prefix, and
     *   2. do not contain '$' except in prefix where it is explicitly written by user, and
     *   3. have same term/type kind as name prefix given so far
     *
     *  The reason for (2) is that we do not want to present compiler-synthesized identifiers
     *  as completion results. However, if a user explicitly writes all '$' characters in an
     *  identifier, we should complete the rest.
     */
    def include(sym: Symbol) =
      sym.name.startsWith(prefix) &&
      !sym.name.toString.drop(prefix.length).contains('$') &&
      (!termOnly || sym.isTerm) &&
      (!typeOnly || sym.isType)

    def enter(sym: Symbol) =
      if (include(sym)) completions.enter(sym)

    def add(sym: Symbol) =
      if (sym.exists && !completions.lookup(sym.name).exists) enter(sym)

    def addMember(site: Type, name: Name) =
      if (!completions.lookup(name).exists)
        for (alt <- site.member(name).alternatives) enter(alt.symbol)

    def accessibleMembers(site: Type, superAccess: Boolean = true): Seq[Symbol] = site match {
      case site: NamedType if site.symbol.is(Package) =>
        site.decls.toList.filter(include) // Don't look inside package members -- it's too expensive.
      case _ =>
        def appendMemberSyms(name: Name, buf: mutable.Buffer[SingleDenotation]): Unit =
          try buf ++= site.member(name).alternatives
          catch { case ex: TypeError => }
        site.memberDenots(takeAllFilter, appendMemberSyms).collect {
          case mbr if include(mbr.symbol) => mbr.accessibleFrom(site, superAccess).symbol
          case _ => NoSymbol
        }.filter(_.exists)
    }

    def addAccessibleMembers(site: Type, superAccess: Boolean = true): Unit =
      for (mbr <- accessibleMembers(site)) addMember(site, mbr.name)

    def getImportCompletions(ictx: Context): Unit = {
      implicit val ctx = ictx
      val imp = ctx.importInfo
      if (imp != null) {
        def addImport(name: TermName) = {
          addMember(imp.site, name)
          addMember(imp.site, name.toTypeName)
        }
        // FIXME: We need to also take renamed items into account for completions,
        // That means we have to return list of a pairs (Name, Symbol) instead of a list
        // of symbols from `completions`.!=
        for (imported <- imp.originals if !imp.excluded.contains(imported)) addImport(imported)
        if (imp.isWildcardImport)
          for (mbr <- accessibleMembers(imp.site) if !imp.excluded.contains(mbr.name.toTermName))
            addMember(imp.site, mbr.name)
      }
    }

    def getScopeCompletions(ictx: Context): Unit = {
      implicit val ctx = ictx

      if (ctx.owner.isClass) {
        addAccessibleMembers(ctx.owner.thisType)
        ctx.owner.asClass.classInfo.selfInfo match {
          case selfSym: Symbol => add(selfSym)
          case _ =>
        }
      }
      else if (ctx.scope != null) ctx.scope.foreach(add)

      getImportCompletions(ctx)

      var outer = ctx.outer
      while ((outer.owner `eq` ctx.owner) && (outer.scope `eq` ctx.scope)) {
        getImportCompletions(outer)
        outer = outer.outer
      }
      if (outer `ne` NoContext) getScopeCompletions(outer)
    }

    def implicitConversionTargets(qual: Tree)(implicit ctx: Context): Set[Type] = {
      val typer = ctx.typer
      val conversions = new typer.ImplicitSearch(defn.AnyType, qual, pos.pos).allImplicits
      val targets = conversions.map(_.widen.finalResultType)
      interactiv.println(i"implicit conversion targets considered: ${targets.toList}%, %")
      targets
    }

    def getMemberCompletions(qual: Tree): Unit = {
      addAccessibleMembers(qual.tpe)
      implicitConversionTargets(qual)(ctx.fresh.setExploreTyperState())
        .foreach(addAccessibleMembers(_))
    }

    path match {
      case (sel @ Select(qual, _)) :: _ => getMemberCompletions(qual)
      case _  => getScopeCompletions(ctx)
    }
    interactiv.println(i"completion with pos = $pos, prefix = $prefix, termOnly = $termOnly, typeOnly = $typeOnly = ${completions.toList}%, %")
    (completionPos, completions.toList)
  }

  /** Possible completions of members of `prefix` which are accessible when called inside `boundary` */
  def completions(prefix: Type, boundary: Symbol)(implicit ctx: Context): List[Symbol] =
    safely {
      if (boundary != NoSymbol) {
        val boundaryCtx = ctx.withOwner(boundary)
        def exclude(sym: Symbol) = sym.isAbsent || sym.is(Synthetic) || sym.is(Artifact)
        def addMember(name: Name, buf: mutable.Buffer[SingleDenotation]): Unit =
          buf ++= prefix.member(name).altsWith(d =>
            !exclude(d) && d.symbol.isAccessibleFrom(prefix)(boundaryCtx))
          prefix.memberDenots(completionsFilter, addMember).map(_.symbol).toList
      }
      else Nil
    }

  /** Filter for names that should appear when looking for completions. */
  private[this] object completionsFilter extends NameFilter {
    def apply(pre: Type, name: Name)(implicit ctx: Context): Boolean =
      !name.isConstructorName && name.toTermName.info.kind == SimpleNameKind
  }

  /** Find named trees with a non-empty position whose symbol match `sym` in `trees`.
   *
   *  Note that nothing will be found for symbols not defined in source code,
   *  use `sourceSymbol` to get a symbol related to `sym` that is defined in
   *  source code.
   */
  def namedTrees(trees: List[SourceTree], include: Include.Set, sym: Symbol)
   (implicit ctx: Context): List[SourceTree] =
    if (!sym.exists)
      Nil
    else
      namedTrees(trees, (include & Include.references) != 0, matchSymbol(_, sym, include))

  /** Find named trees with a non-empty position whose name contains `nameSubstring` in `trees`.
   *
   *  @param includeReferences  If true, include references and not just definitions
   */
  def namedTrees(trees: List[SourceTree], includeReferences: Boolean, nameSubstring: String)
   (implicit ctx: Context): List[SourceTree] =
    namedTrees(trees, includeReferences, _.show.toString.contains(nameSubstring))

  /** Find named trees with a non-empty position satisfying `treePredicate` in `trees`.
   *
   *  @param includeReferences  If true, include references and not just definitions
   */
  def namedTrees(trees: List[SourceTree], includeReferences: Boolean, treePredicate: NameTree => Boolean)
    (implicit ctx: Context): List[SourceTree] = safely {
    val buf = new mutable.ListBuffer[SourceTree]

    trees foreach { case SourceTree(_, topTree, source) =>
      new untpd.TreeTraverser {
        override def traverse(tree: untpd.Tree)(implicit ctx: Context) = {
          tree match {
            case utree: untpd.NameTree if tree.hasType =>
              val tree = utree.asInstanceOf[tpd.NameTree]
              if (tree.symbol.exists
                   && !tree.symbol.is(Synthetic)
                   && tree.pos.exists
                   && !tree.pos.isZeroExtent
                   && (includeReferences || isDefinition(tree))
                   && treePredicate(tree))
                buf += SourceTree(Nil, tree, source)
              traverseChildren(tree)
            case tree: untpd.Inlined =>
              traverse(tree.call)
            case _ =>
              traverseChildren(tree)
          }
        }
      }.traverse(topTree)
    }

    buf.toList
  }

  /** The reverse path to the node that closest encloses position `pos`,
   *  or `Nil` if no such path exists. If a non-empty path is returned it starts with
   *  the tree closest enclosing `pos` and ends with an element of `trees`.
   */
  def pathTo(trees: List[SourceTree], pos: SourcePosition)(implicit ctx: Context): List[Tree] = {
    val allTrees = trees.flatMap(t => t.tree :: t.topLevelImports)
    allTrees.find(t => t.pos.contains(pos.pos)) match {
      case Some(tree) => pathTo(tree, pos.pos)
      case None => Nil
    }
  }

  def pathTo(tree: Tree, pos: Position)(implicit ctx: Context): List[Tree] =
    if (tree.pos.contains(pos))
      NavigateAST.pathTo(pos, tree, skipZeroExtent = true)
        .collect { case t: untpd.Tree => t }
        .dropWhile(!_.hasType).asInstanceOf[List[tpd.Tree]]
    else Nil

  def contextOfStat(stats: List[Tree], stat: Tree, exprOwner: Symbol, ctx: Context): Context = stats match {
    case Nil =>
      ctx
    case first :: _ if first eq stat =>
      ctx.exprContext(stat, exprOwner)
    case (imp: Import) :: rest =>
      contextOfStat(rest, stat, exprOwner, ctx.importContext(imp, imp.symbol(ctx)))
    case _ :: rest =>
      contextOfStat(rest, stat, exprOwner, ctx)
  }

  def contextOfPath(path: List[Tree])(implicit ctx: Context): Context = path match {
    case Nil | _ :: Nil =>
      ctx.run.runContext.fresh.setCompilationUnit(ctx.compilationUnit)
    case nested :: encl :: rest =>
      import typer.Typer._
      val outer = contextOfPath(encl :: rest)
      try encl match {
        case tree @ PackageDef(pkg, stats) =>
          assert(tree.symbol.exists)
          if (nested `eq` pkg) outer
          else contextOfStat(stats, nested, pkg.symbol.moduleClass, outer.packageContext(tree, tree.symbol))
        case tree: DefDef =>
          assert(tree.symbol.exists)
          val localCtx = outer.localContext(tree, tree.symbol).setNewScope
          for (tparam <- tree.tparams) localCtx.enter(tparam.symbol)
          for (vparams <- tree.vparamss; vparam <- vparams) localCtx.enter(vparam.symbol)
            // Note: this overapproximates visibility a bit, since value parameters are only visible
            // in subsequent parameter sections
          localCtx
        case tree: MemberDef =>
          assert(tree.symbol.exists)
          outer.localContext(tree, tree.symbol)
        case tree @ Block(stats, expr) =>
          val localCtx = outer.fresh.setNewScope
          stats.foreach {
            case stat: MemberDef => localCtx.enter(stat.symbol)
            case _ =>
          }
          contextOfStat(stats, nested, ctx.owner, localCtx)
        case tree @ CaseDef(pat, guard, rhs) if nested `eq` rhs =>
          val localCtx = outer.fresh.setNewScope
          pat.foreachSubTree {
            case bind: Bind => localCtx.enter(bind.symbol)
            case _ =>
          }
          localCtx
        case tree @ Template(constr, parents, self, _) =>
          if ((constr :: self :: parents).contains(nested)) ctx
          else contextOfStat(tree.body, nested, tree.symbol, outer.inClassContext(self.symbol))
        case _ =>
          outer
      }
      catch {
        case ex: CyclicReference => outer
      }
  }

  /** The first tree in the path that is a definition. */
  def enclosingDefinitionInPath(path: List[Tree])(implicit ctx: Context): Tree =
    path.find(_.isInstanceOf[DefTree]).getOrElse(EmptyTree)
}
