package dotty.tools
package languageserver

import com.microsoft.java.debug.core._
import com.microsoft.java.debug.core.adapter._

import java.net.URI

import java.util.logging.Level
import java.util.logging.Logger

class DottySourceLookUpProvider(languageServer: DottyLanguageServer) extends ISourceLookUpProvider {
  val logger = Logger.getLogger("java-debug")

  override def getFullyQualifiedName(uri: String, lines: Array[Int], columns: Array[Int]): Array[String] = {
    logger.setLevel(Level.FINEST)
    logger.log(Level.SEVERE, "uri: " + uri)
    List.fill(lines.length)("dotty.tools.dotc.Driver").toArray
  }
  override def getSourceContents(uri: String): String = languageServer.synchronized {
    logger.setLevel(Level.FINEST)
    logger.log(Level.SEVERE, "sources: " + languageServer.sources)
    languageServer.sources(new URI(uri))
  }

  override def getSourceFileURI(fullyQualifiedName: String, sourcePath: String): String = languageServer.synchronized {
    logger.setLevel(Level.FINEST)
    logger.log(Level.SEVERE, "fqn: " + fullyQualifiedName)
    logger.log(Level.SEVERE, "sp: " + sourcePath)
    ""
  }

  override def supportsRealtimeBreakpointVerification(): Boolean = true
}
