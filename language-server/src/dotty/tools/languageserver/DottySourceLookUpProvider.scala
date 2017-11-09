package dotty.tools
package languageserver

import com.microsoft.java.debug.core._
import com.microsoft.java.debug.core.adapter._

import java.net.URI
import java.nio.file._
import java.nio.charset.StandardCharsets

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
      val sourceCode = getSourceContents(uriString)
      val diags = driver.run(uri, sourceCode)
      println("diags: " + diags)

      implicit val ctx = driver.currentCtx

      val columns =
        if (optionalColumns == null)
          List.fill(lines.length)(0).toArray
        else
          optionalColumns

      (lines, columns).zipped.map { (line, column) =>
        val pos = DottyLanguageServer.sourcePosition(driver, uri, line - 1, column)
        val trees = driver.openedTrees(uri)
        val defSym = ctx.atPhase(ctx.typerPhase) { implicit ctx =>
          val path = Interactive.pathTo(trees, pos)
          Interactive.enclosingDefinitionInPath(path).symbol
          // Interactive.enclosingSourceSymbol(driver.openedTrees(uri), pos)
        }
        println("sym: " + defSym)
        if (defSym == NoSymbol) ""
        else {
          // TODO: Confirm that this is a robust way to get the name of the enclosing emitted class
          // val cls = ctx.atPhase(ctx.typerPhase) { implicit ctx =>
          //   sym.enclosingClass
          // }
          ctx.atPhase(ctx.terminalPhase) { implicit ctx =>
            val c = defSym.enclosingClass.fullName.mangledString
            println("c: " + c)
            c
          }
        }
      }
    }

  override def getSourceContents(uriString: String): String = {
    val uri = new URI(uriString)

    new String(Files.readAllBytes(Paths.get(uri)), StandardCharsets.UTF_8)
  }

  override def getSourceFileURI(fullyQualifiedName: String, relativeSourcePath: String): String =
    languageServer.synchronized {
      System.err.println("fqn: " + fullyQualifiedName)
      System.err.println("rsp: " + relativeSourcePath)
      languageServer.drivers.keys.flatMap(_.sourceDirectories).foreach { sourceDir =>
        val path = sourceDir.toPath.resolve(relativeSourcePath)
        if (Files.exists(path)) {
          System.err.println("found: " + path)
          return path.toUri.toString
        }
      }
      System.err.println("not found")
      ""
    }
  override def supportsRealtimeBreakpointVerification: Boolean = true
}
