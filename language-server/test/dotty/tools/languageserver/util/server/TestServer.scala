package dotty.tools.languageserver.util.server

import java.io.PrintWriter
import java.net.URI
import java.nio.file.Path
import java.util

import dotty.tools.languageserver.DottyLanguageServer
import org.eclipse.lsp4j._

import scala.collection.JavaConverters._

class TestServer(testFolder: Path) {

  // Fill the configuration with values populated by sbt
  private def showSeq[T](lst: Seq[T]): String = lst.map(elem => '"' + elem.toString + '"').mkString("[ ", ", ", " ]")
  private val dottyIdeJson: String =
    s"""[ {
       |  "id" : "dotty-ide-test",
       |  "compilerVersion" : "${BuildInfo.ideTestsCompilerVersion}",
       |  "compilerArguments" : ${showSeq(BuildInfo.ideTestsCompilerArguments)},
       |  "sourceDirectories" : ${showSeq(BuildInfo.ideTestsSourceDirectories)},
       |  "dependencyClasspath" : ${showSeq(BuildInfo.ideTestsDependencyClasspath)},
       |  "classDirectory" : "${BuildInfo.ideTestsClassDirectory}"
       |}
       |]
    """.stripMargin
  private val configFile = testFolder.resolve(DottyLanguageServer.IDE_CONFIG_FILE)
  testFolder.toFile.mkdirs()
  testFolder.resolve("src").toFile.mkdirs()
  testFolder.resolve("out").toFile.mkdirs()
  new PrintWriter(configFile.toString) { write(dottyIdeJson); close() }

  val server = new DottyLanguageServer
  private val client = new TestClient
  server.connect(client)

  private val initParams = new InitializeParams()
  initParams.setRootUri(testFolder.toAbsolutePath.toUri.toString)
  server.initialize(initParams).get()

  /** Open the code in the given file and returns the file.
   *  @param code code in file
   *  @param fileName file path in the source directory
   *  @return the file opened
   */
  def openCode(code: String, fileName: String): TestFile = {
    val testFile = new TestFile(fileName)
    val dotdp = new DidOpenTextDocumentParams()
    val tdi = new TextDocumentItem()
    tdi.setUri(testFile.uri)
    tdi.setText(code)
    dotdp.setTextDocument(tdi)
    server.didOpen(dotdp)
    testFile
  }

}
