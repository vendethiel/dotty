import java.net.URI
import java.nio.file._
import java.util.function.Consumer
import java.util.concurrent.CompletableFuture

import io.typefox.lsapi._
import io.typefox.lsapi.impl._
import io.typefox.lsapi.services._
import io.typefox.lsapi.annotations._

import java.util.{List => jList}
import java.util.ArrayList

import dotty.tools.dotc._
import dotty.tools.dotc.core.Contexts._
import dotty.tools.dotc.{ Main => DottyMain }
import dotty.tools.dotc.interfaces
import dotty.tools.dotc.reporting._

import scala.collection._

import dotty.tools.FatalError
import dotty.tools.io._
import scala.io.Codec
import dotty.tools.dotc.util.SourceFile
import java.io._

class ScalaLanguageServer extends LanguageServer { thisServer =>
  val driver = new ServerDriver

  var publishDiagnostics: Consumer[PublishDiagnosticsParams] = _

  override def exit(): Unit = {
    println("exit")
    System.exit(0)
  }
  override def shutdown(): Unit = {
    println("shutdown")
  }

  override def onTelemetryEvent(consumer: Consumer[Object]): Unit = {}

  override def initialize(params: InitializeParams): CompletableFuture[InitializeResult] = {
    val result = new InitializeResultImpl
    val c = new ServerCapabilitiesImpl

    c.setTextDocumentSync(TextDocumentSyncKind.Full)

    c.setDefinitionProvider(true)
    c.setCompletionProvider(new CompletionOptionsImpl())
    c.setHoverProvider(true)
    c.setWorkspaceSymbolProvider(true)
    c.setReferencesProvider(true)
    c.setDocumentSymbolProvider(true)
    result.setCapabilities(c)

    CompletableFuture.completedFuture(result)
  }

  override def getTextDocumentService(): TextDocumentService = new TextDocumentService {
    override def codeAction(params: CodeActionParams): CompletableFuture[jList[_ <: Command]] = null
    override def codeLens(params: CodeLensParams): CompletableFuture[jList[_ <: CodeLens]] = null
    override def completion(params: TextDocumentPositionParams): CompletableFuture[CompletionList] = null
    override def definition(params: TextDocumentPositionParams): CompletableFuture[jList[_ <: Location]] = null
    override def didChange(params: DidChangeTextDocumentParams): Unit = {
      val document = params.getTextDocument
      val uri = URI.create(document.getUri)
      val path = Paths.get(uri)
      println("hii: " + path)
      val change = params.getContentChanges.get(0)
      assert(change.getRange == null, "TextDocumentSyncKind.Incremental support is not implemented")
      val text = change.getText

      val diagnostics = new mutable.ArrayBuffer[DiagnosticImpl]
      driver.run(uri, text, new ServerReporter(thisServer, diagnostics))

      val p = new PublishDiagnosticsParamsImpl
      p.setDiagnostics(java.util.Arrays.asList(diagnostics.toSeq: _*))
      p.setUri(document.getUri)

      publishDiagnostics.accept(p)
    }
    override def didClose(params: DidCloseTextDocumentParams): Unit = {}
    override def didOpen(params: DidOpenTextDocumentParams): Unit = {
      val document = params.getTextDocument
      val uri = URI.create(document.getUri)
      val path = Paths.get(uri)
      println("open: " + path)
      val text = params.getTextDocument.getText

      //.setReporter(new ServerReporter)

      val diagnostics = new mutable.ArrayBuffer[DiagnosticImpl]
      driver.run(uri, text, new ServerReporter(thisServer, diagnostics))

      val p = new PublishDiagnosticsParamsImpl
      p.setDiagnostics(java.util.Arrays.asList(diagnostics.toSeq: _*))
      p.setUri(document.getUri)

      publishDiagnostics.accept(p)
    }
    override def didSave(params: DidSaveTextDocumentParams): Unit = {}
    override def documentHighlight(params: TextDocumentPositionParams): CompletableFuture[DocumentHighlight] = null
    override def documentSymbol(params: DocumentSymbolParams): CompletableFuture[jList[_ <: SymbolInformation]] = null
    override def formatting(params: DocumentFormattingParams): CompletableFuture[jList[_ <: TextEdit]] = null
    override def hover(params: TextDocumentPositionParams): CompletableFuture[Hover] = null
    override def onPublishDiagnostics(callback: Consumer[PublishDiagnosticsParams]): Unit = {
      publishDiagnostics = callback
    }
    override def onTypeFormatting(params: DocumentOnTypeFormattingParams): CompletableFuture[jList[_ <: TextEdit]] = null
    override def rangeFormatting(params: DocumentRangeFormattingParams): CompletableFuture[jList[_ <: TextEdit]] = null
    override def references(params: ReferenceParams): CompletableFuture[jList[_ <: Location]] = null
    override def rename(params: RenameParams): CompletableFuture[WorkspaceEdit] = null
    override def resolveCodeLens(params: CodeLens): CompletableFuture[CodeLens] = null
    override def resolveCompletionItem(params: CompletionItem): CompletableFuture[CompletionItem] = null
    override def signatureHelp(params: TextDocumentPositionParams): CompletableFuture[SignatureHelp] = null
  }

  override def getWindowService(): WindowService = new WindowService {
    override def onLogMessage(callback: Consumer[MessageParams]): Unit = {}
    override def onShowMessage(callback: Consumer[MessageParams]): Unit = {}
    override def onShowMessageRequest(callback: Consumer[ShowMessageRequestParams]): Unit = {}
  }

  override def getWorkspaceService(): WorkspaceService = new WorkspaceService {
    override def didChangeConfiguraton(params: DidChangeConfigurationParams): Unit = {}
    override def didChangeWatchedFiles(params: DidChangeWatchedFilesParams): Unit = {}
    override def symbol(params: WorkspaceSymbolParams): CompletableFuture[jList[_ <: SymbolInformation]] = null
  }
}

class ServerDriver extends Driver {
  override def newCompiler(implicit ctx: Context): Compiler = ???
  override def sourcesRequired = false

  val ctx: Context = {
    val rootCtx = initCtx.fresh
    setup(Array("-Ystop-after:frontend"), rootCtx)._2
  }
  val compiler: Compiler = new Compiler

  def run(uri: URI, sourceCode: String, reporter: Reporter): Reporter = {
    implicit val ictx: Context = ctx.fresh.setReporter(reporter)
    try {
      val run = compiler.newRun
      val virtualFile = new VirtualFile(uri.toString, Paths.get(uri).toString)
      val writer = new BufferedWriter(new OutputStreamWriter(virtualFile.output, "UTF-8"))
      writer.write(sourceCode)
      writer.close()
      val encoding = Codec.UTF8 // Not sure how to get the encoding from the client
      run.compileSources(List(new SourceFile(virtualFile, encoding)))
      run.printSummary()
    }
    catch {
      case ex: FatalError  =>
        ictx.error(ex.getMessage) // signals that we should fail compilation.
        ictx.reporter
    }
    //doCompile(compiler, fileNames)(ctx.fresh.setReporter(reporter))
  }
}

class ServerReporter(server: ScalaLanguageServer, diagnostics: mutable.ArrayBuffer[DiagnosticImpl]) extends Reporter
    with UniqueMessagePositions
    with HideNonSensicalMessages {
  def doReport(d: reporting.Diagnostic)(implicit ctx: Context): Unit = d match {
    case _ =>
      println("doReport: " + d)
      val di = new DiagnosticImpl

      di.setSeverity(severity(d.level))
      if (d.pos.exists) di.setRange(range(d.pos))
      di.setCode("0")
      di.setMessage(d.message)

      if (d.pos.exists) diagnostics += di

      // p.setDiagnostics(java.util.Arrays.asList(di))
      // p.setUri(d.pos.source.file.name)

      //println("publish: " + p)
      // server.publishDiagnostics.accept(p)
  }
  private def severity(level: Int): DiagnosticSeverity = level match {
    case interfaces.Diagnostic.INFO => DiagnosticSeverity.Information
    case interfaces.Diagnostic.WARNING => DiagnosticSeverity.Warning
    case interfaces.Diagnostic.ERROR => DiagnosticSeverity.Error
  }
  private def range(p: dotty.tools.dotc.util.SourcePosition): RangeImpl = {
    val start = new PositionImpl
    start.setLine(p.startLine)
    start.setCharacter(p.startColumn)
    val end = new PositionImpl
    end.setLine(p.endLine)
    end.setCharacter(p.endColumn)

    val range = new RangeImpl
    range.setStart(start)
    range.setEnd(end)
    range
  }
}