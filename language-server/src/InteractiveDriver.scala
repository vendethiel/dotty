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

class ServerDriver(settings: List[String]) extends Driver {
  import ast.tpd._
  import ScalaLanguageServer._

  override protected def newCompiler(implicit ctx: Context): Compiler = ???
  override def sourcesRequired = false

  val openClasses = new mutable.LinkedHashMap[URI, List[TypeName]]

  val openFiles = new mutable.LinkedHashMap[URI, SourceFile]

  def sourcePosition(uri: URI, pos: lsp4j.Position): SourcePosition = {

    val source = openFiles(uri) // might throw exception
    val p = Positions.Position(source.lineToOffset(pos.getLine) + pos.getCharacter)
    new SourcePosition(source, p)
  }

  var myCtx: Context = _

  private[this] def newCtx: Context = ctx

  implicit def ctx: Context =
    if (myCtx == null) {
      val rootCtx = initCtx.fresh.addMode(Mode.ReadPositions)
      setup(settings.toArray, rootCtx)._2
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
          if (!fromSource && openClasses.contains(toUri(sourceFile))) {
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

    val sourceClasses = openClasses.values.flatten
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

  def enclosingSym(paths: List[Tree]): Symbol = {
    val sym = paths.dropWhile(!_.symbol.exists).head.symbol
    if (sym.isLocalDummy) sym.owner
    else sym
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
            if (/*!t.isInstanceOf[Template] && !t.isInstanceOf[TypeDef] && */t.symbol.exists &&
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
      openFiles(uri) = sourceFile

      run.compileSources(List(sourceFile))
      run.printSummary()
      val t = run.units.head.tpdTree
      openClasses(uri) = topLevelClassNames(t)
      t
    }
    catch {
      case ex: FatalError  =>
        ctx.error(ex.getMessage) // signals that we should fail compilation.
        EmptyTree
    }
    //doCompile(compiler, fileNames)(ctx.fresh.setReporter(reporter))
  }
}
