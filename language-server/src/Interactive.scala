import java.net.URI
import java.nio.file._
import java.util.function._
import java.util.concurrent.CompletableFuture

import org.eclipse.lsp4j
import org.eclipse.lsp4j.jsonrpc.{CancelChecker, CompletableFutures}
import org.eclipse.lsp4j._
import org.eclipse.lsp4j.services._

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

import ast.NavigateAST._

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
  def pathTo(spos: SourcePosition, trees: List[SourceTree](implicit ctx: Context): List[Tree] =
    trees.find({ case SourceTree(source, t) =>
      source == spos.source && t.pos.contains(spos.pos)
    }) match {
      case Some(tree) =>
        // FIXME: We shouldn't need a cast. Change NavigateAST.pathTo to return a List of Tree.
        // We assume that typed trees only contain typed trees but this is incorrect in two
        // cases currently: `Super` and `This` have `untpd.Ident` members, if we could
        // replace those by typed members it would avoid complicating the Interactive API.
        pathTo(spos.pos, tree).asInstanceOf[List[Tree]]
      case _ =>
        Nil
    }

   /** The type of the closest enclosing tree containing position `spos`.
    *
    *  If that type is `NoType` we return `None`, except if `keepGoing` is true,
    *  then we return the type of the closest enclosing tree which does have a
    *  type.
    */
  def enclosingType(spos: SourcePosition, trees: List[SourceTree], keepGoing: Boolean = false)(implicit ctx: Context): Option[Type] = {
    val path0 = pathTo(spos, trees)
    val path =
      if (keepGoing)
        path.dropWhile(_.tpe eq NoType)
      else
        path
    path.headOption.flatMap(tree =>
      if (tree.tpe ne NoType)
        Some(tree.tpe)
      else
        None
    )
  }

  /** The symbol of the closest enclosing tree containing position `spos`.
    *
    *  If that symbol is `NoSymbol` we return `None`, except if `keepGoing` is true,
    *  then we return the symbol of the closest enclosing tree which does have a
    *  type.
    */
  def enclosingSymbol(spos: SourcePosition, trees: List[SourceTree], keepGoing: Boolean = false)(implicit ctx: Context): Option[Type] = {
    val path0 = pathTo(spos, trees)
    val path =
      if (keepGoing)
        path.dropWhile(_.tpe eq NoSymbol)
      else
        path
    path.headOption.flatMap(tree =>
      if (tree.tpe ne NoSymbol)
        Some(tree.tpe)
      else
        None
    )
  }

  def completions(spos: SourcePosition, trees: List[SourceTree])(implicit ctx: Context): List[Symbol] = {
    val paths = pathTo(spos, trees)

    val sourceTree = paths.last
    val boundary = enclosingSymbol(spos, List(paths.last), keepGoing = true)

    paths.head match {
      case Select(qual, _) =>
        val boundary = enclosingSymbol(spos, sourceTree)
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

  def completions(prefix: Type, boundary: Symbol)(implicit ctx: Context): List[Symbol] = {
    val boundaryCtx = ctx.withOwner(boundary)
    prefix.memberDenots(completionsFilter, (name, buf) =>
      buf ++= prefix.member(name).altsWith(_.symbol.isAccessibleFrom(prefix)(boundaryCtx))
    ).map(_.symbol).toList
  }

  def definitions(sym: Symbol, trees: List[SourceTree])(implicit ctx: Context): List[SourcePosition] = {
    val poss = new mutable.ListBuffer[SourcePosition]

    if (sym.exists) {
      trees foreach { case SourceTree(sourceFile, tree) =>
        object finder extends TreeTraverser {
          override def traverse(tree: Tree)(implicit ctx: Context): Unit = tree match {
            case t: MemberDef =>
              if (t.symbol.eq(sym) || t.symbol.allOverriddenSymbols.contains(sym)) {
                poss += new SourcePosition(sourceFile, t.namePos)
              } else {
                traverseChildren(tree)
              }
            case _ =>
              traverseChildren(tree)
          }
        }
        finder.traverse(tree)
      }
    }
    poss.toList
  }

  /** Find all references to `sym` or to a symbol overriding `sym`
   */
  def references(sym: Symbol, includeDeclaration: Boolean, trees: List[SourceTree])
      (implicit ctx: Context): List[SourcePosition] = {
    val poss = new mutable.ListBuffer[SourcePosition]

    if (sym.exists) {
      trees foreach { case SourceTree(sourceFile, tree) =>
        object extract extends TreeTraverser {
          override def traverse(tree: Tree)(implicit ctx: Context): Unit = tree match {
            case t if t.pos.exists =>
              if (t.symbol.exists &&
                (t.symbol.eq(sym) || t.symbol.allOverriddenSymbols.contains(sym))) {
                if (!t.isInstanceOf[MemberDef] || includeDeclaration) {
                  poss += new SourcePosition(sourceFile, t.pos)
                }
              } else {
                traverseChildren(tree)
              }
            case _ =>
              traverseChildren(tree)
          }
        }
        extract.traverse(tree)
      }
    }
    poss.toList
  }

  def rename(sym: Symbol, newName: String)(implicit ctx: Context): List[SourcePosition] = ???
}
