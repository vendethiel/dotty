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
import dotty.tools.dotc.util._
import dotty.tools.dotc.core.Contexts._
import dotty.tools.dotc.core.SymDenotations._
import dotty.tools.dotc.core._
import dotty.tools.dotc.core.tasty._
import dotty.tools.dotc.{ Main => DottyMain }
import dotty.tools.dotc.interfaces
import dotty.tools.dotc.reporting._
import dotty.tools.dotc.reporting.diagnostic._

import scala.collection._
import scala.collection.JavaConverters._

import dotty.tools.FatalError
import dotty.tools.io._
import scala.io.Codec
import dotty.tools.dotc.util.SourceFile
import java.io._

import core.Decorators._

import ast.Trees._


class ScalaLanguageServer extends LanguageServer { thisServer =>
  import ScalaLanguageServer._
  val driver = new ServerDriver(this)

  var publishDiagnostics: Consumer[PublishDiagnosticsParams] = _
  var rewrites: dotty.tools.dotc.rewrite.Rewrites = _

  val actions = new mutable.LinkedHashMap[io.typefox.lsapi.Diagnostic, Command]

  var classPath: String = _
  var target: String = _

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

    c.setTextDocumentSync(TextDocumentSyncKind.Full)

    c.setDefinitionProvider(true)
    // val clOptions = new CodeLensOptionsImpl
    // clOptions.setResolveProvider(true)
    // c.setCodeLensProvider(clOptions)
    c.setCompletionProvider(new CompletionOptionsImpl)
    //c.setHoverProvider(true)
    c.setCodeActionProvider(true)
    c.setWorkspaceSymbolProvider(true)
    c.setReferencesProvider(true)
    c.setDocumentSymbolProvider(true)
    result.setCapabilities(c)

    CompletableFuture.completedFuture(result)
  }

  override def getTextDocumentService(): TextDocumentService = new TextDocumentService {
    override def codeAction(params: CodeActionParams): CompletableFuture[jList[_ <: Command]] = {
      println("#########params: " + params)
      println("#########actions: " + actions)
      val l = params.getContext.getDiagnostics.asScala.flatMap{d =>
        actions.get(d)
      }
      println("l: " + l)
      CompletableFuture.completedFuture(l.asJava)
    }
    override def codeLens(params: CodeLensParams): CompletableFuture[jList[_ <: CodeLens]] = {
      // val cl = new mutable.ArrayBuffer[CodeLensImpl]


      // println("rewrites " + rewrites.patched)
      // rewrites.patched.values.toList match {
      //   case List(value) =>
      //     val patches = value.pbuf.toList.sortBy(_.pos.start)
      //     println("patches: " + patches)
      //     for (patch <- patches) {
      //       val c = new CodeLensImpl
      //       val r = range(new SourcePosition(value.source, patch.pos))
      //       c.setRange(r)
      //       val command = new CommandImpl
      //       command.setTitle("Rewrite")
      //       //command.setCommand(s"Replace ${patch.replacement}")
      //       command.setCommand("ext")
      //       c.setCommand(command)
      //       cl += c
      //     }
      //   case _ =>
      // }
      /*
      {
        val start = new PositionImpl
        start.setLine(2)
        start.setCharacter(1)
        val end = new PositionImpl
        end.setLine(2)
        end.setCharacter(10)

        val range = new RangeImpl
        range.setStart(start)
        range.setEnd(end)
        c.setRange(range)
      }

      {
        val command = new CommandImpl
        command.setTitle("Hello")
        command.setCommand("Actual command")
        c.setCommand(command)
      }*/

      //CompletableFuture.completedFuture(java.util.Arrays.asList(c))
      null//CompletableFuture.completedFuture(java.util.Arrays.asList(cl: _*))
    }
    override def completion(params: TextDocumentPositionParams): CompletableFuture[CompletionList] = null
    override def definition(params: TextDocumentPositionParams): CompletableFuture[jList[_ <: Location]] = null
    override def didChange(params: DidChangeTextDocumentParams): Unit = {
      val document = params.getTextDocument
      val uri = URI.create(document.getUri)
      val path = Paths.get(uri)
      val change = params.getContentChanges.get(0)
      assert(change.getRange == null, "TextDocumentSyncKind.Incremental support is not implemented")
      val text = change.getText

      val diagnostics = new mutable.ArrayBuffer[DiagnosticImpl]
      val ctx = driver.run(uri, text, new ServerReporter(thisServer, diagnostics))

      rewrites = ctx.settings.rewrite.value(ctx).get // EVIL

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
      val ctx = driver.run(uri, text, new ServerReporter(thisServer, diagnostics))

      rewrites = ctx.settings.rewrite.value(ctx).get // EVIL

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
    override def references(params: ReferenceParams): CompletableFuture[jList[_ <: Location]] = {
      val poss = driver.references
      val locs = poss.map(location)
      CompletableFuture.completedFuture(locs.asJava)
    }
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

class ServerDriver(server: ScalaLanguageServer) extends Driver {
  import ast.tpd._

  override def newCompiler(implicit ctx: Context): Compiler = ???
  override def sourcesRequired = false

  lazy val ctx: Context = {
    val rootCtx = initCtx.fresh
    // setup(Array("-Ystop-after:frontend", "-language:Scala2", "-rewrite", "-classpath", "/home/smarter/opt/dotty-example-project/target/scala-2.11/classes/"), rootCtx)._2
    setup(Array("-Ystop-after:frontend", "-language:Scala2", "-rewrite", "-classpath", server.target + ":" + server.classPath), rootCtx)._2
  }
  val compiler: Compiler = new Compiler

  def references: List[SourcePosition] = {
    //implicit val ictx: Context = ctx.fresh
    //ictx.initialize
    val run = compiler.newRun(ctx.fresh.setReporter(new ConsoleReporter))
    implicit val ictx: Context = run.runContext
    val classNames = ictx.platform.classPath.classes.map(_.name.toTypeName)
    classNames.flatMap({ className =>
      val clsd =
        if (className.contains('.')) ictx.base.staticRef(className)
        else ictx.definitions.EmptyPackageClass.info.decl(className)
      def cannotUnpickle(reason: String) = {
        println(s"class $className cannot be unpickled because $reason")
        Nil
      }
      clsd match {
        case clsd: ClassDenotation =>
          clsd.infoOrCompleter match {
            case info: ClassfileLoader =>
              info.load(clsd)
            /*
              info.load(clsd) match {
                case Some(unpickler: DottyUnpickler) =>
                  val List(unpickled) = unpickler.body(ictx.addMode(Mode.ReadPositions))
                  //println("unpickled: " + unpickled.show)
                  val PackageDef(_, cls :: _) = unpickled
                  val sourceFile = new SourceFile(cls.symbol.sourceFile, Codec.UTF8)
                  val ps = positions(unpickled)
                  val sourcePoss = ps.map(p => new SourcePosition(sourceFile, p))
                  sourcePoss
                // val unit1 = new CompilationUnit(new SourceFile(clsd.symbol.sourceFile, Seq()))
                // unit1.tpdTree = unpickled
                // unit1.unpicklers += (clsd.classSymbol -> unpickler.unpickler)
                // force.traverse(unit1.tpdTree)
                // unit1
                case _ =>
                  cannotUnpickle(s"its class file ${info.classfile} does not have a TASTY attribute")
              }
              */
            case info =>
              //cannotUnpickle(s"its info of type ${info.getClass} is not a ClassfileLoader")
          }
          val tree = clsd.symbol.tree
          if (tree != null) {
            println("Got tree: " + clsd)
            val clsTrees = tree match {
              case PackageDef(_, clss) => clss
              case cls: TypeDef => List(cls)
            }
            clsTrees.flatMap({ clsTree =>
              val sourceFile = new SourceFile(clsTree.symbol.sourceFile, Codec.UTF8)
              val ps = positions(clsTree)
              val sourcePoss = ps.map(p => new SourcePosition(sourceFile, p))
              sourcePoss
            })
          } else {
            println("no tree: " + clsd)
            Nil
          }
        case _ =>
          sys.error(s"class not found: $className")
      }
    }).toList
  }

  private def positions(tree: Tree)(implicit ctx: Context): List[Positions.Position] = {
    val poss = new mutable.ListBuffer[Positions.Position]
    object extract extends TreeTraverser {
      override def traverse(tree: Tree)(implicit ctx: Context): Unit = tree match {
        case t: PackageDef =>
          traverseChildren(t)
        case t @ TypeDef(_, tmpl : Template) =>
          if (t.pos.exists /*&& !t.pos.isSynthetic*/) poss += t.pos
          traverseChildren(tmpl)
        case t: DefDef =>
          if (t.pos.exists /*&& !t.pos.isSynthetic*/) poss += t.pos
        case t: ValDef =>
          if (t.pos.exists /*&& !t.pos.isSynthetic*/) poss += t.pos
        case _ =>
      }
    }
    extract.traverse(tree)
    poss.toList
  }

  def run(uri: URI, sourceCode: String, reporter: Reporter): Context = {
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
      ictx
    }
    catch {
      case ex: FatalError  =>
        ictx.error(ex.getMessage) // signals that we should fail compilation.
        ictx
    }
    //doCompile(compiler, fileNames)(ctx.fresh.setReporter(reporter))
  }
}

class ServerReporter(server: ScalaLanguageServer, diagnostics: mutable.ArrayBuffer[DiagnosticImpl]) extends Reporter
    with UniqueMessagePositions
    with HideNonSensicalMessages {
  def doReport(cont: MessageContainer)(implicit ctx: Context): Unit = cont match {
    case _ =>
      println("doReport: " + cont)
      val di = new DiagnosticImpl

      di.setSeverity(severity(cont.level))
      if (cont.pos.exists) di.setRange(ScalaLanguageServer.range(cont.pos))
      di.setCode("0")
      di.setMessage(cont.message)

      if (cont.pos.exists) diagnostics += di

      cont.patch match {
        case Some(patch) =>
          val c = new CommandImpl
          c.setTitle("Rewrite")
          val uri = cont.pos.source.name
          val r = ScalaLanguageServer.range(new SourcePosition(cont.pos.source, patch.pos))
          c.setCommand("dotty.fix")
          c.setArguments(List[Object](uri, r, patch.replacement).asJava)
          server.actions += di -> c
        case _ =>
      }

      // p.setDiagnostics(java.util.Arrays.asList(di))
      // p.setUri(cont.pos.source.file.name)

      //println("publish: " + p)
      // server.publishDiagnostics.accept(p)
  }
  private def severity(level: Int): DiagnosticSeverity = level match {
    case interfaces.Diagnostic.INFO => DiagnosticSeverity.Information
    case interfaces.Diagnostic.WARNING => DiagnosticSeverity.Warning
    case interfaces.Diagnostic.ERROR => DiagnosticSeverity.Error
  }
}
object ScalaLanguageServer {
  def range(p: SourcePosition): RangeImpl = {
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
  def location(p: SourcePosition) = {
    val l = new LocationImpl
    l.setUri(Paths.get(p.source.file.path).toUri.toString)
    l.setRange(range(p))
    l
  }
}