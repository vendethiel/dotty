package dotty.tools
package languageserver

import com.microsoft.java.debug.core._
import com.microsoft.java.debug.core.adapter._

import java.net.URI
import java.nio.file._
import java.nio.charset.StandardCharsets

import java.util.logging.Level
import java.util.logging.Logger

import dotc._
import typer.FrontEnd
import dotty.tools.backend.jvm.GenBCode
import dotc.core._
import StdNames._
import Phases._
import core.Constants._
import dotc.ast.{NavigateAST, untpd}
import dotc.core.Contexts._
import dotc.core.Symbols.NoSymbol
import dotc.interactive.Interactive
import dotc.parsing.Parsers.Parser
import dotc.parsing.Tokens
import dotc.util.Positions.Position
import dotc.util.SourceFile

import com.sun.jdi._

// Copy-pasted from REPLFrontEnd
class DebugFrontEnd extends FrontEnd {
  override def phaseName = "debugFrontEnd"

  override def isRunnable(implicit ctx: Context) = true

  override def runOn(units: List[CompilationUnit])(implicit ctx: Context) = {
    val unitContexts = for (unit <- units) yield ctx.fresh.setCompilationUnit(unit)
    var remaining = unitContexts
    while (remaining.nonEmpty) {
      enterSyms(remaining.head)
      remaining = remaining.tail
    }
    unitContexts.foreach(enterAnnotations(_))
    unitContexts.foreach(typeCheck(_))
    unitContexts.map(_.compilationUnit).filterNot(discardAfterTyper)
  }
}

class DebugCompiler(exprPos: Position) extends Compiler {
  override def phases: List[List[Phase]] =
    List(new DebugFrontEnd) ::
    super.phases.init.tail ++ List(
      List(new Extract(exprPos)),
      List(new GenBCode)
    )

  val GlobalCode =
"""
package dbg {
  import scala.collection.immutable._

  class Global {
    def makeMap(names: Array[String], locals: Array[Object]): Map[String, Object] =
      (names, locals).zipped.toMap
  }
}
"""

  val GlobalSourceFile = new SourceFile("<dbg.Global>", GlobalCode.toCharArray)


  // def compileTree(tree: untpd.Tree, sourceCode: String, run: Run, runCtx: Context) = {
  //   // FIXME: Duplication with ReplCompiler
  //   val unit = new CompilationUnit(new SourceFile("virt", sourceCode))
  //   unit.untpdTree = tree

  //   val dbgUnit = new CompilationUnit(GlobalSourceFile)
  //   dbgUnit.untpdTree = new Parser(dbgUnit.source)(runCtx).parse()

  //   run.compileUnits(List(unit, dbgUnit))
  // }

  def compileTree(curTree: untpd.Tree, sourceCode: String, run: Run, runCtx: Context) = {
    // FIXME: Duplication with ReplCompiler

    val globalTree = new Parser(GlobalSourceFile)(runCtx).parse()

    val unit = new CompilationUnit(new SourceFile("virt", sourceCode))

    unit.untpdTree = untpd.PackageDef(untpd.Ident(nme.EMPTY_PACKAGE),
      List(globalTree, curTree))

    run.compileUnits(List(unit))
  }
}

object DebugDriver extends Driver {
  override def newCompiler(implicit ctx: Context): Compiler = ???
  override protected def sourcesRequired = false
}

class DottyEvaluationProvider(languageServer: DottyLanguageServer, sourceLookup: DottySourceLookUpProvider) extends IEvaluationProvider {
  val logger = Logger.getLogger("java-debug")

  private def parse(code: String)(implicit ctx: Context): List[untpd.Tree] = {
    // FIXME: copy-pasted from ParseResult
    def parseStats(parser: Parser): List[untpd.Tree] = {
      val stats = parser.blockStatSeq()
      parser.accept(Tokens.EOF)
      stats
    }

    val source = new SourceFile("<console>", code.toCharArray)
    val parser = new Parser(source)

    parseStats(parser)
  }

  override def eval(code: String, sf: StackFrame, listener: IEvaluationListener): String = {
    val loc = sf.location
    val line = loc.lineNumber
    println("line: " + line)
    println("sp: " + loc.sourcePath)
    val uri = new URI(sourceLookup.getSourceFileURI(""/*unused*/, loc.sourcePath))
    val driver = languageServer.debugDriverFor(uri)
    implicit val ctx = driver.currentCtx

    // FIXME: Duplication with DottySourceLookUpProvider#getFullyQualifiedName
    val debugPos = DottyLanguageServer.sourcePosition(driver, uri, line - 1, column = 0)


    val tree = driver.openedUntypedTree(uri)
    val newTree = new untpd.UntypedTreeMap {
      import untpd._

      override def transform(tree: Tree)(implicit ctx: Context): Tree =
        if (tree.pos.contains(debugPos.pos))
          super.transform(tree)
        else
          tree

      override def transformStats(trees: List[Tree], ownerPos: Position)(implicit ctx: Context): List[Tree] = {
        if (ownerPos.contains(debugPos.pos)) {
          val trees1 = super.transformStats(trees, ownerPos)
          if (trees1 eq trees) {
            val (beforeTrees, afterTrees) = trees.partition(statTree => statTree.pos.end < debugPos.pos.start)
            println("beforeTrees: " + beforeTrees.map(_.show))
            println("afterTrees: " + afterTrees.map(_.show))
            val stats = parse(code)
            println("#Stats: " + stats)
            beforeTrees ++ (untpd.Block(stats, Literal(Constant(()))).withPos(debugPos.pos) :: afterTrees)
          }
          else
            trees1
        }
        else
          trees
      }
    }.transform(tree)

    println("##NEW TREE#########################################")
    println(newTree.show)
    println("##DONE#########################################")

    {
      val rootCtx = (new ContextBase).initialCtx
      val settings = driver.settings ++ List("-Xprint:all", "-d", "/home/smarter/tmp")
      val runCtx: Context = DebugDriver.setup(settings.toArray, rootCtx)._2

      val compiler = new DebugCompiler(debugPos.pos)
      val run = compiler.newRun(runCtx)

      val sourceCode = sourceLookup.getSourceContents(uri.toString)
      compiler.compileTree(newTree, sourceCode, run, runCtx)
      run.printSummary()
    }

    // val diags = driver.run(uri, sourceCode)
    // println("diags: " + diags)

    // val path = trees.flatMap(tree => NavigateAST.pathTo(pos.pos, tree)).asInstanceOf[List[untpd.Tree]]
    // System.err.println("pos: " + pos.pos)
    // System.err.println("path: " + path.map(_.show).mkString("#################\n"))

    // ctx.atPhase(ctx.typerPhase) { implicit ctx =>
    // }

    val res = "duck"
    listener.evaluationComplete(res)
    res
  }

  override def isInEvaluation(thread: ThreadReference) = false
}
