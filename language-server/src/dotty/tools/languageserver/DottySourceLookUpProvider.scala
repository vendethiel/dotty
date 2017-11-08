package dotty.tools
package languageserver

import com.microsoft.java.debug.core._
import com.microsoft.java.debug.core.adapter._

import java.net.URI

import java.util.logging.Level
import java.util.logging.Logger

import dotc.core.Symbols.NoSymbol
import dotc.interactive.Interactive

class DottySourceLookUpProvider(languageServer: DottyLanguageServer) extends ISourceLookUpProvider {
  val logger = Logger.getLogger("java-debug")

  override def getFullyQualifiedName(uriString: String, lines: Array[Int], optionalColumns: Array[Int]): Array[String] =
    languageServer.synchronized {
      val uri = new URI(uriString)
      val driver = languageServer.debugDriverFor(uri)
      val diags = driver.run(uri, languageServer.sources(uri))
      println("diags: " + diags)

      implicit val ctx = driver.currentCtx

      val columns =
        if (optionalColumns == null)
          List.fill(lines.length)(0).toArray
        else
          optionalColumns

      System.err.println("lines: " + lines.toList)
      System.err.println("columns: " + columns.toList)
      (lines, columns).zipped.map { (line, column) =>
        val pos = DottyLanguageServer.sourcePosition(driver, uri, line - 1, column)
        val sym = ctx.atPhase(ctx.typerPhase) { implicit ctx =>
          Interactive.enclosingSourceSymbol(driver.openedTrees(uri), pos)
        }
        System.err.println("pos: " + pos)
        System.err.println("sym: " + sym)
        // val path = ctx.atPhase(ctx.typerPhase) { implicit ctx =>
        //   println("path: " + Interactive.pathTo(driver.openedTrees(uri), pos).map(_.show).mkString("##\n"))
        // }
        if (sym == NoSymbol) ""
        else {
          // TODO: Confirm that this is a robust way to get the name of the enclosing emitted class
          val meth = ctx.atPhase(ctx.typerPhase) { implicit ctx =>
            sym.enclosingMethod
          }
          ctx.atPhase(ctx.terminalPhase) { implicit ctx =>
            System.err.println("owner1: " + meth.enclosingClass.fullName.mangledString)
            meth.enclosingClass.fullName.mangledString
          }
        }
      }
    }

  override def getSourceContents(uriString: String): String = languageServer.synchronized {
    val uri = new URI(uriString)

    languageServer.sources(uri)
  }

  override def getSourceFileURI(fullyQualifiedName: String, sourcePath: String): String = languageServer.synchronized {
    System.err.println("fqn: " + fullyQualifiedName)
    ""
  }

  override def supportsRealtimeBreakpointVerification(): Boolean = false
}
