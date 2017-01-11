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


class ScalaLanguageServer extends LanguageServer with LanguageClientAware { thisServer =>
  import ast.tpd._

  import ScalaLanguageServer._
  val driver = new ServerDriver(this)

  var publishDiagnostics: PublishDiagnosticsParams => Unit = _
  var rewrites: dotty.tools.dotc.rewrite.Rewrites = _

  val actions = new mutable.LinkedHashMap[org.eclipse.lsp4j.Diagnostic, Command]

  var classPath: String = _
  var target: String = _

  val openClasses = new mutable.LinkedHashMap[URI, List[TypeName]]

  val openFiles = new mutable.LinkedHashMap[URI, SourceFile]

  def sourcePosition(uri: URI, pos: lsp4j.Position): SourcePosition = {
    val source = openFiles(uri) // might throw exception
    val p = Positions.Position(source.lineToOffset(pos.getLine) + pos.getCharacter)
    new SourcePosition(source, p)
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
      fun(cancelToken)
    }.asJava)

  override def initialize(params: InitializeParams): CompletableFuture[InitializeResult] = computeAsync { cancelToken =>
    val result = new InitializeResult
    val c = new ServerCapabilities

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
    // val clOptions = new CodeLensOptions
    // clOptions.setResolveProvider(true)
    // c.setCodeLensProvider(clOptions)
    c.setCompletionProvider(new CompletionOptions)
    c.setRenameProvider(true)
    c.setHoverProvider(true)
    c.setCodeActionProvider(true)
    c.setWorkspaceSymbolProvider(true)
    c.setReferencesProvider(true)
    //c.setDocumentSymbolProvider(true)
    result.setCapabilities(c)

    result
  }

  override def getTextDocumentService(): TextDocumentService = new TextDocumentService {
    override def codeAction(params: CodeActionParams): CompletableFuture[jList[_ <: Command]] = computeAsync { cancelToken =>
      val l = params.getContext.getDiagnostics.asScala.flatMap{d =>
        actions.get(d)
      }
      l.asJava
    }
    override def codeLens(params: CodeLensParams): CompletableFuture[jList[_ <: CodeLens]] = null
    override def completion(params: TextDocumentPositionParams): CompletableFuture[CompletionList] = null
    override def definition(params: TextDocumentPositionParams): CompletableFuture[jList[_ <: Location]] = computeAsync { cancelToken =>
      val trees = driver.trees
      val pos = sourcePosition(new URI(params.getTextDocument.getUri), params.getPosition)
      val tp = driver.typeOf(trees, pos)
      //println("XXPATHS: " + paths.map(_.show))
      println("Looking for: " + tp)
      if (tp eq NoType)
        List[Location]().asJava
      else {
        val defPos = driver.findDef(trees, tp)
        if (defPos.pos == Positions.NoPosition)
          List[Location]().asJava
        else
          List(location(defPos)).asJava
      }
    }
    override def didChange(params: DidChangeTextDocumentParams): Unit = {
      val document = params.getTextDocument
      val uri = URI.create(document.getUri)
      val path = Paths.get(uri)
      val change = params.getContentChanges.get(0)
      assert(change.getRange == null, "TextDocumentSyncKind.Incremental support is not implemented")
      val text = change.getText

      val diagnostics = new mutable.ArrayBuffer[lsp4j.Diagnostic]
      val tree = driver.run(uri, text, new ServerReporter(thisServer, diagnostics))

      // TODO: Do this for didOpen too ? (So move to driver.run like openFiles)
      openClasses(uri) = driver.topLevelClassNames(tree)

      val p = new PublishDiagnosticsParams
      p.setDiagnostics(java.util.Arrays.asList(diagnostics.toSeq: _*))
      p.setUri(document.getUri)

      publishDiagnostics(p)
    }
    override def didClose(params: DidCloseTextDocumentParams): Unit = {
      val document = params.getTextDocument
      val uri = URI.create(document.getUri)

      openClasses.remove(uri)
      openFiles.remove(uri)
    }
    override def didOpen(params: DidOpenTextDocumentParams): Unit = {
      val document = params.getTextDocument
      val uri = URI.create(document.getUri)
      val path = Paths.get(uri)
      println("open: " + path)
      val text = params.getTextDocument.getText

      //.setReporter(new ServerReporter)

      val diagnostics = new mutable.ArrayBuffer[lsp4j.Diagnostic]
      val tree = driver.run(uri, text, new ServerReporter(thisServer, diagnostics))

      openClasses(uri) = driver.topLevelClassNames(tree)

      val p = new PublishDiagnosticsParams
      p.setDiagnostics(java.util.Arrays.asList(diagnostics.toSeq: _*))
      p.setUri(document.getUri)

      publishDiagnostics(p)
    }
    override def didSave(params: DidSaveTextDocumentParams): Unit = {}
    override def documentHighlight(params: TextDocumentPositionParams): CompletableFuture[jList[_ <: DocumentHighlight]] = null
    override def documentSymbol(params: DocumentSymbolParams): CompletableFuture[jList[_ <: SymbolInformation]] = null
    override def formatting(params: DocumentFormattingParams): CompletableFuture[jList[_ <: TextEdit]] = null
    override def hover(params: TextDocumentPositionParams): CompletableFuture[Hover] = {
      implicit val ctx: Context = driver.ctx

      val pos = sourcePosition(new URI(params.getTextDocument.getUri), params.getPosition)
      val tp = driver.typeOf(driver.trees, pos)
      println("hover: " + tp.show)

      val h = new Hover
      val str = tp.widenTermRefExpr.show.toString
      h.setContents(List(str).asJava)
      //h.setRange()
      CompletableFuture.completedFuture(h)
    }

    override def onTypeFormatting(params: DocumentOnTypeFormattingParams): CompletableFuture[jList[_ <: TextEdit]] = null
    override def rangeFormatting(params: DocumentRangeFormattingParams): CompletableFuture[jList[_ <: TextEdit]] = null
    override def references(params: ReferenceParams): CompletableFuture[jList[_ <: Location]] = computeAsync { cancelToken =>
      val trees = driver.trees
      val pos = sourcePosition(new URI(params.getTextDocument.getUri), params.getPosition)

      val tp = driver.typeOf(trees, pos)

      println("Find all references to " + tp)
      val poss = driver.typeReferences(trees, tp, params.getContext.isIncludeDeclaration)
      val locs = poss.map(location)
      locs.asJava
    }
    override def rename(params: RenameParams): CompletableFuture[WorkspaceEdit] = computeAsync { cancelToken =>
      val trees = driver.trees
      val pos = sourcePosition(new URI(params.getTextDocument.getUri), params.getPosition)

      val tp = driver.typeOf(trees, pos)
      implicit val ctx: Context = driver.ctx
      val sym = tp.asInstanceOf[NamedType].symbol

      val name = params.getNewName

      val changes = new mutable.HashMap[String, jList[TextEdit]]

      val w = new WorkspaceEdit
      w.setChanges(changes.asJava)
      w
    }
    override def resolveCodeLens(params: CodeLens): CompletableFuture[CodeLens] = null
    override def resolveCompletionItem(params: CompletionItem): CompletableFuture[CompletionItem] = null
    override def signatureHelp(params: TextDocumentPositionParams): CompletableFuture[SignatureHelp] = null
  }

  override def getWorkspaceService(): WorkspaceService = new WorkspaceService {
    override def didChangeConfiguration(params: DidChangeConfigurationParams): Unit = {}
    override def didChangeWatchedFiles(params: DidChangeWatchedFilesParams): Unit = {}
    override def symbol(params: WorkspaceSymbolParams): CompletableFuture[jList[_ <: SymbolInformation]] = computeAsync { cancelToken =>
      val trees = driver.trees
      val syms = driver.symbolInfos(trees, params.getQuery)
      syms.asJava
    }
  }
}

class ServerDriver(server: ScalaLanguageServer) extends Driver {
  import ast.tpd._
  import ScalaLanguageServer._

  override def newCompiler(implicit ctx: Context): Compiler = ???
  override def sourcesRequired = false

  var myCtx: Context = _

  private[this] def newCtx: Context = {
    // val rootCtx = initCtx.fresh
    // setup(Array("-Ystop-after:frontend", "-language:Scala2", "-rewrite", "-classpath", server.target + ":" + server.classPath), rootCtx)._2
    ctx
  }

  implicit def ctx: Context =
    if (myCtx == null) {
      val rootCtx = initCtx.fresh.addMode(Mode.ReadPositions)
      val toolcp = "../library/target/scala-2.11/classes"
      // setup(Array("-Ystop-after:frontend", "-language:Scala2", "-rewrite", "-classpath", "/home/smarter/opt/dotty-example-project/target/scala-2.11/classes/"), rootCtx)._2
      setup(Array(/*"-Yplain-printer",*//*"-Yprintpos",*/ "-Ylog:frontend", "-Ystop-after:frontend", "-language:Scala2", "-rewrite", "-classpath", toolcp + ":" + server.target + ":" + server.classPath), rootCtx)._2
    } else
      myCtx

  val compiler: Compiler = new Compiler

  def typeOf(trees: List[(SourceFile, Tree)], pos: SourcePosition): Type = {
    import ast.NavigateAST._

    val tree = trees.filter({ case (source, t) =>
      source == pos.source && {
        //println("###pos: " + pos.pos)
        //println("###tree: " + t.show(ctx.fresh.setSetting(ctx.settings.Yprintpos, true).setSetting(ctx.settings.YplainPrinter, true)))
        t.pos.contains(pos.pos)
      }}).head._2
    val paths = pathTo(pos.pos, tree).asInstanceOf[List[Tree]]
    //println("###paths: " + paths.map(x => (x.show, x.tpe.show)))
    val t = paths.head
    paths.head.tpe
  }

  def tree(className: TypeName, fromSource: Boolean): Option[(SourceFile, Tree)] = {
    println(s"tree($className, $fromSource)")
    val clsd =
      if (className.contains('.')) ctx.base.staticRef(className)
      else ctx.definitions.EmptyPackageClass.info.decl(className)
    def cannotUnpickle(reason: String) = {
      println(s"class $className cannot be unpickled because $reason")
      Nil
    }
    clsd match {
      case clsd: ClassDenotation =>
        clsd.info // force denotation
        val tree = clsd.symbol.tree
        if (tree != null) {
          println("Got tree: " + clsd)// + " " + tree.show)
          assert(tree.isInstanceOf[TypeDef])
          //List(tree).foreach(t => println(t.symbol, t.symbol.validFor))
          val sourceFile = new SourceFile(tree.symbol.sourceFile, Codec.UTF8)
          if (!fromSource && server.openClasses.contains(toUri(sourceFile))) {
            //println("Skipping, already open from source")
            None
          } else
            Some((sourceFile, tree))
        } else {
          println("no tree: " + clsd)
          None
        }
      case _ =>
        sys.error(s"class not found: $className")
    }
  }

  def trees = {
    //implicit val ictx: Context = ctx.fresh
    //ictx.initialize

    //val run = compiler.newRun(newCtx.fresh.setReporter(new ConsoleReporter))
    //myCtx = run.runContext

    println("##ALL: " + ctx.definitions.EmptyPackageClass.info.decls)

    val sourceClasses = server.openClasses.values.flatten
    val tastyClasses = ctx.platform.classPath.classes.map(_.name.toTypeName)

    (sourceClasses.flatMap(c => tree(c, fromSource = true)) ++
      tastyClasses.flatMap(c => tree(c, fromSource = false))).toList
  }

  def symbolInfos(trees: List[(SourceFile, Tree)], query: String): List[SymbolInformation] = {
    //println("#####symbolInfos: " + ctx.period)

    val syms = new mutable.ListBuffer[SymbolInformation]

    trees foreach { case (sourceFile, tree) =>
      object extract extends TreeTraverser {
        override def traverse(tree: Tree)(implicit ctx: Context): Unit = tree match {
          case t @ TypeDef(_, tmpl : Template) =>
            if (t.symbol.exists && t.pos.exists && t.symbol.name.toString.contains(query) /*&& !t.pos.isSynthetic*/) syms += symbolInfo(sourceFile, t)
            traverseChildren(tmpl)
          case t: TypeDef =>
            if (t.symbol.exists && t.pos.exists && t.symbol.name.toString.contains(query) /*&& !t.pos.isSynthetic*/) syms += symbolInfo(sourceFile, t)
          case t: DefDef =>
            if (t.symbol.exists && t.pos.exists && t.symbol.name.toString.contains(query) /*&& !t.pos.isSynthetic*/) syms += symbolInfo(sourceFile, t)
          case t: ValDef =>
            if (t.symbol.exists && t.pos.exists && t.symbol.name.toString.contains(query) /*&& !t.pos.isSynthetic*/) syms += symbolInfo(sourceFile, t)
          case _ =>
        }
      }
      extract.traverse(tree)
    }
    syms.toList
  }

  def findDef(trees: List[(SourceFile, Tree)], tp: Type): SourcePosition = {
    val sym = tp match {
      case tp: NamedType => tp.symbol
      case _ => return NoSourcePosition
    }

    var pos: SourcePosition = NoSourcePosition

    trees foreach { case (sourceFile, tree) =>
      object finder extends TreeTraverser {
        override def traverse(tree: Tree)(implicit ctx: Context): Unit = tree match {
          case t: MemberDef =>
            //println("FOUND: " + t.tpe  + " " + t.symbol + " " + t.pos)
            // if (t.tpe =:= tp) {
            // TypeDef have fixed sym from older run
            if (/*!t.isInstanceOf[TypeDef] &&*/ t.symbol.eq(sym)) {
              pos = new SourcePosition(sourceFile, t.namePos)
              return
            } else {
              traverseChildren(tree)
            }
          // case t: MemberDef =>
          //   println("wrong: " + t.tpe + " != " + tp)
          //   traverseChildren(tree)
          case _ =>
            traverseChildren(tree)
        }
      }
      finder.traverse(tree)
    }
    pos
  }

  def typeReferences(trees: List[(SourceFile, Tree)], tp: Type, includeDeclaration: Boolean): List[SourcePosition] = {
    val sym = tp match {
      case tp: NamedType => tp.symbol
      case _ => return Nil
    }
    //println("#####symbolInfos: " + ctx.period)

    val poss = new mutable.ListBuffer[SourcePosition]

    trees foreach { case (sourceFile, tree) =>
      object extract extends TreeTraverser {
        override def traverse(tree: Tree)(implicit ctx: Context): Unit = tree match {
          case t if t.pos.exists =>
            // Template and TypeDef have FixedSym from older run
            // if (!t.isInstanceOf[Template] && t.tpe <:< tp) {
            if (/*!t.isInstanceOf[Template] && !t.isInstanceOf[TypeDef] && */t.symbol.exists && t.symbol.eq(sym)) {
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

  def references: List[SourcePosition] = {
    //println("#####references: " + ctx.period)

    //implicit val ictx: Context = ctx.fresh
    //ictx.initialize
    val run = compiler.newRun(newCtx.fresh.setReporter(new ConsoleReporter))
    myCtx = run.runContext

    val classNames = ctx.platform.classPath.classes.map(_.name.toTypeName)
    classNames.flatMap({ className =>
      val clsd =
        if (className.contains('.')) ctx.base.staticRef(className)
        else ctx.definitions.EmptyPackageClass.info.decl(className)
      def cannotUnpickle(reason: String) = {
        println(s"class $className cannot be unpickled because $reason")
        Nil
      }
      clsd match {
        case clsd: ClassDenotation =>
          clsd.infoOrCompleter match {
            case info: ClassfileLoader =>
              info.load(clsd)
            case _ =>
          }
          val tree = clsd.symbol.tree
          if (tree != null) {
            //println("Got tree: " + clsd)
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
            //println("no tree: " + clsd)
            Nil
          }
        case _ =>
          sys.error(s"class not found: $className")
      }
    }).toList
  }

  def topLevelClassNames(tree: Tree): List[TypeName] = {
    val names = new mutable.ListBuffer[TypeName]
    object extract extends TreeTraverser {
      override def traverse(tree: Tree)(implicit ctx: Context): Unit = tree match {
        case t: PackageDef =>
          traverseChildren(t)
        case t @ TypeDef(_, tmpl : Template) =>
          names += t.symbol.name.asTypeName
        case _ =>
      }
    }
    extract.traverse(tree)
    names.toList
  }

  private def positions(tree: Tree): List[Positions.Position] = {
    //println("#####positions: " + ctx.period)

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

  def run(uri: URI, sourceCode: String, reporter: Reporter): Tree = {
    try {
      println("run: " + ctx.period)

      val run = compiler.newRun(newCtx.fresh.setReporter(reporter))
      myCtx = run.runContext

      val virtualFile = new VirtualFile(uri.toString, Paths.get(uri).toString)
      val writer = new BufferedWriter(new OutputStreamWriter(virtualFile.output, "UTF-8"))
      writer.write(sourceCode)
      writer.close()
      val encoding = Codec.UTF8 // Not sure how to get the encoding from the client
      val sourceFile = new SourceFile(virtualFile, encoding)
      server.openFiles(uri) = sourceFile
      run.compileSources(List(sourceFile))
      run.printSummary()
      run.units.head.tpdTree
    }
    catch {
      case ex: FatalError  =>
        ctx.error(ex.getMessage) // signals that we should fail compilation.
        EmptyTree
    }
    //doCompile(compiler, fileNames)(ctx.fresh.setReporter(reporter))
  }
}

class ServerReporter(server: ScalaLanguageServer, diagnostics: mutable.ArrayBuffer[lsp4j.Diagnostic]) extends Reporter
    with UniqueMessagePositions
    with HideNonSensicalMessages {
  def doReport(cont: MessageContainer)(implicit ctx: Context): Unit = cont match {
    case _ =>
      println("doReport: " + cont)
      val di = new lsp4j.Diagnostic

      di.setSeverity(severity(cont.level))
      if (cont.pos.exists) di.setRange(ScalaLanguageServer.range(cont.pos))
      di.setCode("0")
      di.setMessage(cont.message)

      if (cont.pos.exists) diagnostics += di

      cont.patch match {
        case Some(patch) =>
          val c = new Command
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
      // server.publishDiagnostics(p)
  }
  private def severity(level: Int): DiagnosticSeverity = level match {
    case interfaces.Diagnostic.INFO => DiagnosticSeverity.Information
    case interfaces.Diagnostic.WARNING => DiagnosticSeverity.Warning
    case interfaces.Diagnostic.ERROR => DiagnosticSeverity.Error
  }
}
object ScalaLanguageServer {
  import ast.tpd._

  def range(p: SourcePosition): Range = {
    val start = new Position
    start.setLine(p.startLine)
    start.setCharacter(p.startColumn)
    val end = new Position
    end.setLine(p.endLine)
    end.setCharacter(p.endColumn)

    val range = new Range
    range.setStart(start)
    range.setEnd(end)
    range
  }
  def toUri(source: SourceFile) = Paths.get(source.file.path).toUri

  def location(p: SourcePosition) = {
    val l = new Location
    l.setUri(toUri(p.source).toString)
    l.setRange(range(p))
    l
  }
//     File = 1,
//     Module = 2,
//     Namespace = 3,
//     Package = 4,
//     Class = 5,
//     Method = 6,
//     Property = 7,
//     Field = 8,
//     Constructor = 9,
//     Enum = 10,
//     Interface = 11,
//     Function = 12,
//     Variable = 13,
//     Constant = 14,
//     String = 15,
//     Number = 16,
//     Boolean = 17,
//     Array = 18,
  def symbolKind(sym: Symbol)(implicit ctx: Context) =
    if (sym.is(Package))
      SymbolKind.Package
    else if (sym.isConstructor)
      SymbolKind.Constructor
    else if (sym.isClass)
      SymbolKind.Class
    else if (sym.is(Mutable))
      SymbolKind.Variable
    else
      SymbolKind.Field

  def symbolInfo(sourceFile: SourceFile, t: Tree)(implicit ctx: Context) = {
    assert(t.pos.exists)
    val sym = t.symbol
    val s = new SymbolInformation
    s.setName(sym.name.toString)
    s.setKind(symbolKind(sym))
    s.setLocation(location(new SourcePosition(sourceFile, t.pos)))
    if (sym.owner.exists && !sym.owner.isEmptyPackage)
      s.setContainerName(sym.owner.name.toString)
    s
  }
}