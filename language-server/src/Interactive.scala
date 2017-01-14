import java.net.URI
import java.nio.file._
import java.util.function._
import java.util.concurrent.CompletableFuture

import java.util.{List => jList}
import java.util.ArrayList

import dotty.tools.dotc._
import dotty.tools.dotc.util._
import dotty.tools.dotc.core.Contexts._
import dotty.tools.dotc.core.SymDenotations._
import dotty.tools.dotc.core._
import dotty.tools.dotc.core.Types._
import dotty.tools.dotc.core.tasty._
import dotty.tools.dotc.{ Main => DottyMain }
import dotty.tools.dotc.interfaces
import dotty.tools.dotc.reporting._
import dotty.tools.dotc.reporting.diagnostic._

import scala.collection._
import scala.collection.JavaConverters._

// Not needed with Scala 2.12
import scala.compat.java8.FunctionConverters._

import dotty.tools.FatalError
import dotty.tools.io._
import scala.io.Codec
import dotty.tools.dotc.util.SourceFile
import java.io._

import Flags._, Symbols._, Names._, NameOps._
import core.Decorators._

import ast.Trees._

import Positions._

import ast.NavigateAST

object Interactive {
  import ast.tpd._

  /** Filter for names that should appear when looking for completions. */
  private[this] object completionsFilter extends NameFilter {
    def apply(pre: Type, name: Name)(implicit ctx: Context): Boolean =
      !name.isConstructorName
  }

  /** The reverse path to the node that closest encloses position `spos`,
   *  or `Nil` if no such path exists. If a non-empty path is returned it starts with
   *  the tree closest enclosing `pos` and ends with an element of `trees`.
   */
  def pathTo(spos: SourcePosition, trees: List[SourceTree])(implicit ctx: Context): List[Tree] =
    trees.find({ case SourceTree(source, t) =>
      source == spos.source && t.pos.contains(spos.pos)
    }).toList.flatMap(stree =>
      // FIXME: We shouldn't need a cast. Change NavigateAST.pathTo to return a List of Tree.
      // We assume that typed trees only contain typed trees but this is incorrect in two
      // cases currently: `Super` and `This` have `untpd.Ident` members, if we could
      // replace those by typed members it would avoid complicating the Interactive API.
      NavigateAST.pathTo(spos.pos, stree.tree).asInstanceOf[List[Tree]]
    )

  /** The type of the closest enclosing tree containing position `spos`. */
  def enclosingType(spos: SourcePosition, trees: List[SourceTree])(implicit ctx: Context): Type = {
    val path = pathTo(spos, trees)
    path.headOption.fold(NoType: Type)(_.tpe)
  }

  /** The symbol of the closest enclosing tree containing position `spos`.
   *
   *  If that symbol is a class local dummy, return the class symbol instead.
   */
  def enclosingSymbol(spos: SourcePosition, trees: List[SourceTree])(implicit ctx: Context): Symbol = {
    val path = pathTo(spos, trees)
    path.headOption.fold(NoSymbol: Symbol)(tree => nonLocalDummy(tree.symbol))
  }

  /** If `sym` is a local dummy, the corresponding class symbol, otherwise `sym` */
  def nonLocalDummy(sym: Symbol)(implicit ctx: Context): Symbol = if (sym.isLocalDummy) sym.owner else sym

  /** The first tree in the path that is a definition. */
  def enclosingDefinitionInPath(path: List[Tree])(implicit ctx: Context): Tree =
    path.find(_.isInstanceOf[DefTree]).getOrElse(EmptyTree)

  /** Possible completions at position `spos` */
  def completions(spos: SourcePosition, trees: List[SourceTree])(implicit ctx: Context): List[Symbol] = {
    val path = pathTo(spos, trees)
    val boundary = enclosingDefinitionInPath(path).symbol

    path.take(1).flatMap {
      case Select(qual, _) =>
        // When completing "`a.foo`, return the members of `a`
        completions(qual.tpe, boundary)
      case _ =>
        // FIXME: Get all decls in current scope
        boundary.enclosingClass match {
          case csym: ClassSymbol =>
            val classRef = csym.classInfo.typeRef
            completions(classRef, boundary)
          case _ =>
            Nil
        }
    }
  }

  /** Possible completions of members of `prefix` which are legal when called from `boundary` */
  def completions(prefix: Type, boundary: Symbol)(implicit ctx: Context): List[Symbol] = {
    val boundaryCtx = ctx.withOwner(boundary)
    prefix.memberDenots(completionsFilter, (name, buf) =>
      buf ++= prefix.member(name).altsWith(_.symbol.isAccessibleFrom(prefix)(boundaryCtx))
    ).map(_.symbol).toList
  }

  def definitions(sym: Symbol, trees: List[SourceTree], fullPosition: Boolean = false)(implicit ctx: Context): List[SourcePosition] = {
    val poss = new mutable.ListBuffer[SourcePosition]

    if (sym.exists) {
      trees foreach { case SourceTree(sourceFile, tree) =>
        object finder extends TreeTraverser {
          override def traverse(tree: Tree)(implicit ctx: Context): Unit = tree match {
            case t: MemberDef if t.symbol.eq(sym) || (tree.symbol.exists && !tree.symbol.is(Synthetic) && t.symbol.allOverriddenSymbols.contains(sym)) =>
              if (tree.pos.exists && tree.pos.start != tree.pos.end) poss += new SourcePosition(sourceFile, t.namePos)
              traverseChildren(tree)
            case _ =>
              traverseChildren(tree)
          }
        }
        finder.traverse(tree)
      }
    }
    poss.toList
  }

  def definitions(query: String, trees: List[SourceTree])(implicit ctx: Context): List[(Symbol, SourcePosition)] = {
    val poss = new mutable.ListBuffer[(Symbol, SourcePosition)]

    trees foreach { case SourceTree(sourceFile, tree) =>
      object finder extends TreeTraverser {
        override def traverse(tree: Tree)(implicit ctx: Context): Unit = tree match {
          case tree: MemberDef if tree.symbol.exists && !tree.symbol.is(Synthetic) && tree.symbol.name.show.toString.contains(query) =>
            if (tree.pos.exists && tree.pos.start != tree.pos.end) poss += ((tree.symbol, new SourcePosition(sourceFile, tree.namePos)))
            traverseChildren(tree)
          case _ =>
            traverseChildren(tree)
        }
      }
      finder.traverse(tree)
    }
    poss.toList
  }

  /** Find all references to `sym` or to a symbol overriding `sym`
   */
  def references(trees: List[SourceTree], sym: Symbol, includeDeclarations: Boolean, namePosition: Boolean = true)
      (implicit ctx: Context): List[SourcePosition] = {
    val poss = new mutable.ListBuffer[SourcePosition]

    if (sym.exists) {
      trees foreach { case SourceTree(sourceFile, tree) =>
        object extract extends TreeTraverser {
          override def traverse(tree: Tree)(implicit ctx: Context): Unit = tree match {
            case tree: NameTree =>
              if ((tree.symbol.eq(sym) || (tree.symbol.exists && !tree.symbol.is(Synthetic) && tree.symbol.allOverriddenSymbols.contains(sym))) &&
                (includeDeclarations || !tree.isInstanceOf[MemberDef]))
                if (tree.pos.exists && tree.pos.start != tree.pos.end) poss += new SourcePosition(sourceFile, displayedPos(tree, namePosition))
              traverseChildren(tree)
            case _ =>
              traverseChildren(tree)
          }
        }
        extract.traverse(tree)
      }
    }
    poss.toList
  }

  def references(query: String, includeDeclarations: Boolean, trees: List[SourceTree])
      (implicit ctx: Context): List[(Symbol, SourcePosition)] = {
    val poss = new mutable.ListBuffer[(Symbol, SourcePosition)]

    trees foreach { case SourceTree(sourceFile, tree) =>
      object extract extends TreeTraverser {
        override def traverse(t: Tree)(implicit ctx: Context): Unit = {
          if (tree.symbol.exists && !tree.symbol.is(Synthetic) && tree.symbol.name.show.toString.contains(query))
            if (tree.pos.exists && tree.pos.start != tree.pos.end) poss += ((tree.symbol, new SourcePosition(sourceFile, tree.pos)))
          traverseChildren(tree)
        }
      }
      extract.traverse(tree)
    }
    poss.toList
  }

  /** Return the position to be displayed. If `namePosition` is true, this is the position of
   *  the name inside `tree`, otherwise it's the position of `tree` itself.
   */
  private[this] def displayedPos(tree: NameTree, namePosition: Boolean)(implicit ctx: Context): Position = {
    val treePos = tree.pos
    val nameLength = tree.name.show.toString.length
    if (namePosition) {
      // FIXME: This is incorrect in some cases, like with backquoted identifiers,
      //        see https://github.com/lampepfl/dotty/pull/1634#issuecomment-257079436
      // FIXME: Merge with NameTree#namePos ?
      val (start, end) =
        if (!treePos.isSynthetic)
          (treePos.point, treePos.point + nameLength)
        else
          // If we don't have a point, we need to find it
          (treePos.end - nameLength, treePos.end)
      Position(start, end, start)
    } else
      treePos
  }
}
