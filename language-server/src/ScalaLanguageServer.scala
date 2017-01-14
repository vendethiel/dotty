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

import scala.util.control.NonFatal

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


class ScalaLanguageServer extends LanguageServer with LanguageClientAware { thisServer =>
  import ast.tpd._

  import ScalaLanguageServer._
  var driver: ServerDriver = _

  var publishDiagnostics: PublishDiagnosticsParams => Unit = _
  var rewrites: dotty.tools.dotc.rewrite.Rewrites = _

  val actions = new mutable.LinkedHashMap[org.eclipse.lsp4j.Diagnostic, Command]

  var classPath: String = _
  var target: String = _

  def diagnostic(cont: MessageContainer): Option[lsp4j.Diagnostic] =
    if (!cont.pos.exists)
      None
    else {
      def severity(level: Int): DiagnosticSeverity = level match {
        case interfaces.Diagnostic.INFO => DiagnosticSeverity.Information
        case interfaces.Diagnostic.WARNING => DiagnosticSeverity.Warning
        case interfaces.Diagnostic.ERROR => DiagnosticSeverity.Error
      }

      val di = new lsp4j.Diagnostic
      di.setSeverity(severity(cont.level))
      if (cont.pos.exists) di.setRange(ScalaLanguageServer.range(cont.pos))
      di.setCode("0")
      di.setMessage(cont.message)

      if (!cont.pos.exists) {
        println("cont: " + cont)
      }

      // if (cont.pos.exists) diagnostics += di

      cont.patch match {
        case Some(patch) =>
          val c = new Command
          c.setTitle("Rewrite")
          val uri = cont.pos.source.name
          val r = ScalaLanguageServer.range(new SourcePosition(cont.pos.source, patch.pos))
          c.setCommand("dotty.fix")
          c.setArguments(List[Object](uri, r, patch.replacement).asJava)
          actions += di -> c
        case _ =>
      }
      Some(di)
    }

  override def connect(client: LanguageClient): Unit = {
    publishDiagnostics = client.publishDiagnostics _
  }

  override def exit(): Unit = {
    println("exit")
    System.exit(0)
  }
  override def shutdown(): CompletableFuture[Object] = {
    println("shutdown")
    CompletableFuture.completedFuture(new Object)
  }

  def computeAsync[R](fun: CancelChecker => R): CompletableFuture[R] =
    CompletableFutures.computeAsync({(cancelToken: CancelChecker) =>
      cancelToken.checkCanceled()
      try {
        fun(cancelToken)
      } catch {
        case NonFatal(ex) =>
          ex.printStackTrace
          throw ex
      }
    }.asJava)

  override def initialize(params: InitializeParams): CompletableFuture[InitializeResult] = computeAsync { cancelToken =>

    val ensime = scala.io.Source.fromURL("file://" + params.getRootPath + "/.ensime").mkString

    classPath = """:compile-deps (.*)""".r.unanchored.findFirstMatchIn(ensime).map(_.group(1)) match {
      case Some(deps) =>
        """"(.*?)"""".r.unanchored.findAllMatchIn(deps).map(_.group(1)).mkString(":")
      case None =>
        //println("XX#: " + ensime + "###")
        ""
    }
    target = """:targets \("(.*)"\)""".r.unanchored.findFirstMatchIn(ensime).map(_.group(1)).get
    println("classPath: " + classPath)
    println("target: " + target)

    val toolcp = "../library/target/scala-2.11/classes"
    driver = new ServerDriver(List(/*"-Yplain-printer",*//*"-Yprintpos",*/ "-Ylog:frontend", "-Ystop-after:frontend", "-language:Scala2", "-rewrite", "-classpath", toolcp + ":" + target + ":" + classPath))


    val c = new ServerCapabilities
    c.setTextDocumentSync(TextDocumentSyncKind.Full)
    c.setDocumentSymbolProvider(true)
    c.setDefinitionProvider(true)
    c.setRenameProvider(true)
    c.setHoverProvider(true)
    c.setCodeActionProvider(true)
    c.setWorkspaceSymbolProvider(true)
    c.setReferencesProvider(true)
    c.setCompletionProvider(new CompletionOptions(
      /* resolveProvider = */ false,
      /* triggerCharacters = */ List(".").asJava))

    new InitializeResult(c)
  }

  override def getTextDocumentService(): TextDocumentService = new TextDocumentService {
    override def codeAction(params: CodeActionParams): CompletableFuture[jList[_ <: Command]] = computeAsync { cancelToken =>
      val l = params.getContext.getDiagnostics.asScala.flatMap{d =>
        actions.get(d)
      }
      l.asJava
    }
    override def codeLens(params: CodeLensParams): CompletableFuture[jList[_ <: CodeLens]] = null
    // FIXME: share code with messages.NotAMember
    override def completion(params: TextDocumentPositionParams): CompletableFuture[CompletionList] = computeAsync { cancelToken =>
      implicit val ctx = driver.ctx

      val trees = driver.trees
      val spos = driver.sourcePosition(new URI(params.getTextDocument.getUri), params.getPosition)

      val decls = Interactive.completions(spos, trees)

      val items = decls.map({ decl =>
        val item = new CompletionItem
        item.setLabel(decl.name.show.toString)
        item.setDetail(decl.info.widenTermRefExpr.show.toString)
        item.setKind(completionItemKind(decl))
        item
      })

      new CompletionList(/*isIncomplete = */ false, items.asJava)
    }
    override def definition(params: TextDocumentPositionParams): CompletableFuture[jList[_ <: Location]] = computeAsync { cancelToken =>
      implicit val ctx = driver.ctx

      val trees = driver.trees
      val spos = driver.sourcePosition(new URI(params.getTextDocument.getUri), params.getPosition)
      val sym = Interactive.enclosingSymbol(spos, trees)

      Interactive.definitions(sym, trees).map(location).asJava
    }
    override def didChange(params: DidChangeTextDocumentParams): Unit = {
      val document = params.getTextDocument
      val uri = URI.create(document.getUri)
      val path = Paths.get(uri)
      val change = params.getContentChanges.get(0)
      assert(change.getRange == null, "TextDocumentSyncKind.Incremental support is not implemented")
      val text = change.getText

      val diags = driver.run(uri, text)


      publishDiagnostics(new PublishDiagnosticsParams(
        document.getUri,
        diags.flatMap(diagnostic).asJava))
    }
    override def didClose(params: DidCloseTextDocumentParams): Unit = {
      val document = params.getTextDocument
      val uri = URI.create(document.getUri)

      driver.close(uri)
    }
    override def didOpen(params: DidOpenTextDocumentParams): Unit = {
      val document = params.getTextDocument
      val uri = URI.create(document.getUri)
      val path = Paths.get(uri)
      println("open: " + path)
      val text = params.getTextDocument.getText

      val diags = driver.run(uri, text)

      publishDiagnostics(new PublishDiagnosticsParams(
        document.getUri,
        diags.flatMap(diagnostic).asJava))
    }
    override def didSave(params: DidSaveTextDocumentParams): Unit = {}
    override def documentHighlight(params: TextDocumentPositionParams): CompletableFuture[jList[_ <: DocumentHighlight]] = null
    override def documentSymbol(params: DocumentSymbolParams): CompletableFuture[jList[_ <: SymbolInformation]] = computeAsync { cancelToken =>
      val document = params.getTextDocument
      val uri = URI.create(document.getUri)

      implicit val ctx = driver.ctx

      val uriTrees = driver.trees.filter(tree => toUri(tree.source) == uri)

      val syms = Interactive.definitions("", uriTrees)
      syms.map({case (sym, spos) => symbolInfo(sym, spos)}).asJava
    }
    override def hover(params: TextDocumentPositionParams): CompletableFuture[Hover] = computeAsync { cancelToken =>
      implicit val ctx = driver.ctx

      val pos = driver.sourcePosition(new URI(params.getTextDocument.getUri), params.getPosition)
      val tp = Interactive.enclosingType(pos, driver.trees)
      println("hover: " + tp.show)

      val str = tp.widenTermRefExpr.show.toString
      new Hover(List(str).asJava, null)
    }

    override def formatting(params: DocumentFormattingParams): CompletableFuture[jList[_ <: TextEdit]] = null
    override def rangeFormatting(params: DocumentRangeFormattingParams): CompletableFuture[jList[_ <: TextEdit]] = null
    override def onTypeFormatting(params: DocumentOnTypeFormattingParams): CompletableFuture[jList[_ <: TextEdit]] = null

    override def references(params: ReferenceParams): CompletableFuture[jList[_ <: Location]] = computeAsync { cancelToken =>
      implicit val ctx = driver.ctx

      val pos = driver.sourcePosition(new URI(params.getTextDocument.getUri), params.getPosition)

      val trees = driver.trees
      val sym = Interactive.enclosingSymbol(pos, trees) 
      val refs = Interactive.references(sym, params.getContext.isIncludeDeclaration, trees)(driver.ctx)

      refs.map(location).asJava
    }
    override def rename(params: RenameParams): CompletableFuture[WorkspaceEdit] = computeAsync { cancelToken =>
      implicit val ctx = driver.ctx

      val trees = driver.trees
      val pos = driver.sourcePosition(new URI(params.getTextDocument.getUri), params.getPosition)


      val sym = Interactive.enclosingSymbol(pos, trees)
      val newName = params.getNewName

      val poss = Interactive.references(sym, includeDeclarations = true, trees)

      val changes = poss.groupBy(pos => toUri(pos.source).toString).mapValues(_.map(pos => new TextEdit(nameRange(pos, sym.name.length), newName)).asJava)

      new WorkspaceEdit(changes.asJava)
    }
    override def resolveCodeLens(params: CodeLens): CompletableFuture[CodeLens] = null
    override def resolveCompletionItem(params: CompletionItem): CompletableFuture[CompletionItem] = null
    override def signatureHelp(params: TextDocumentPositionParams): CompletableFuture[SignatureHelp] = null
  }

  override def getWorkspaceService(): WorkspaceService = new WorkspaceService {
    override def didChangeConfiguration(params: DidChangeConfigurationParams): Unit = {}
    override def didChangeWatchedFiles(params: DidChangeWatchedFilesParams): Unit = {}
    override def symbol(params: WorkspaceSymbolParams): CompletableFuture[jList[_ <: SymbolInformation]] = computeAsync { cancelToken =>
      val query = params.getQuery

      implicit val ctx = driver.ctx

      val syms = Interactive.definitions(query, driver.trees)
      syms.map({case (sym, spos) => symbolInfo(sym, spos)}).asJava
    }
  }
}

object ScalaLanguageServer {
  import ast.tpd._

  def nameRange(p: SourcePosition, nameLength: Int): Range = {
    val (beginName, endName) =
      if (p.pos.isSynthetic)
        (p.pos.end - nameLength, p.pos.end)
      else
        (p.pos.point, p.pos.point + nameLength)

    new Range(
      new Position(p.source.offsetToLine(beginName), p.source.column(beginName)),
      new Position(p.source.offsetToLine(endName), p.source.column(endName))
    )
  }

  def range(p: SourcePosition): Range =
    new Range(
      new Position(p.startLine, p.startColumn),
      new Position(p.endLine, p.endColumn)
    )

  def toUri(source: SourceFile) = Paths.get(source.file.path).toUri

  def location(p: SourcePosition) =
    new Location(toUri(p.source).toString, range(p))

  def symbolKind(sym: Symbol)(implicit ctx: Context) =
    if (sym.is(Package))
      SymbolKind.Package
    else if (sym.isConstructor)
      SymbolKind.Constructor
    else if (sym.isClass)
      SymbolKind.Class
    else if (sym.is(Mutable))
      SymbolKind.Variable
    else if (sym.is(Method))
      SymbolKind.Method
    else
      SymbolKind.Field

  def completionItemKind(sym: Symbol)(implicit ctx: Context) =
    if (sym.is(Package))
      CompletionItemKind.Module // No CompletionItemKind.Package
    else if (sym.isConstructor)
      CompletionItemKind.Constructor
    else if (sym.isClass)
      CompletionItemKind.Class
    else if (sym.is(Mutable))
      CompletionItemKind.Variable
    else if (sym.is(Method))
      CompletionItemKind.Method
    else
      CompletionItemKind.Field

  def symbolInfo(sym: Symbol, spos: SourcePosition)(implicit ctx: Context) = {
    val s = new SymbolInformation
    s.setName(sym.name.show.toString)
    s.setKind(symbolKind(sym))
    s.setLocation(location(spos))
    if (sym.owner.exists && !sym.owner.isEmptyPackage)
      s.setContainerName(sym.owner.name.show.toString)
    s
  }
}
