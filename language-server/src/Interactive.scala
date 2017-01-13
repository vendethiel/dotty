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

import Flags._, Symbols._, Names._
import core.Decorators._

import ast.Trees._

import Positions._

object Interactive {
  import ast.tpd._

  def complete(implicit ctx: Context) = ???


  def definitions(spos: SourcePosition)(implicit ctx: Context): List[SourcePosition] = ???

  /** Find all references to `sym` or to a symbol overriding `sym`
   */
  def references(sym: Symbol, includeDeclaration: Boolean, trees: List[SourceTree])
      (implicit ctx: Context): List[SourcePosition] = {
    val poss = new mutable.ListBuffer[SourcePosition]

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
    poss.toList
  }

  def rename(sym: Symbol, newName: String)(implicit ctx: Context): List[SourcePosition] = ???
}
