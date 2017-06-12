package dotty.tools
package languageserver

import collection.JavaConverters._
import com.sun.jdi._
import com.sun.jdi.event._
import com.sun.jdi.request._

import scala.collection._

import scala.io.Codec
import java.io._
import backend.jvm.GenBCode
import dotty.tools.io._
import dotty.tools.dotc.ast._
import dotty.tools.dotc.interactive._
import dotty.tools.dotc.util._
import dotty.tools.dotc._
import core._
import Contexts._, Phases._, Positions._

class DebugCompiler(exprPos: Position) extends Compiler {
  override def phases: List[List[Phase]] =
    super.phases.init ++ List(
      List(new Extract(exprPos)),
      List(new GenBCode)
    )
}

object DebugDriver extends Driver {
  override def newCompiler(implicit ctx: Context): Compiler = ???
  override protected def sourcesRequired = false
}


// Fundamental limitations: cannot extend outer class
// Easier: cannot define class

/**
  // What about types in trees? Easier after Erasure, or maybe just FirstTransform

  a.fun(b)

  =>
  def expr(a: TypeTree(a.tpe), b) = { // Using types at Typer // XXX: But no reason for type prefixes to still make sense, unless TreeTypeMap deals with it?
    a.fun(b)
  }
  expr(thisWithRefl.$outer.$outer.a.asInstanceOf[firstArgOfExpr], // thisWithRefl like this but refl for calls (NOTE: need to distinguish fields and methods)
       locals("b")) // locals is a map VariableName -> Value
  */
/**
  Alternative: do everything with phases after backend
  // XX: Don't just extract expr, extract everything in pos of expr
  Phase 1: Extract expr to companion with $this and non-local idents as params, original becomes forwarder
  Phase 2: Get rid of all trees not in pos of expr, keep forwarder call around
  Phase 3: Create entry point from forwarder call:
    this -> thisParam
    local x -> locals("x")
    this.a.b -> thisWithRefl.a.b
  */

object DebugMain {
  def collectIdents(tree: untpd.Tree)(implicit ctx: Context) = {
    val buf = new mutable.ListBuffer[untpd.Ident]

    (new untpd.TreeTraverser {
      override def traverse(tree: untpd.Tree)(implicit ctx: Context) = {
        tree match {
          case ident: untpd.Ident =>
            buf += ident
          case _ =>
        }
        traverseChildren(tree)
      }
    }).traverse(tree)

    buf.toList
  }

  val sourceCode =
"""
package dbg

class Foo {
  val str: String = "duck"
  def test: Unit = {
    { def x = 1; x; println(str) }
  }
}

object Global
"""

  def main(args: Array[String]): Unit = {
    val virtualFile = new VirtualFile("<virtual>")
    val writer = new BufferedWriter(new OutputStreamWriter(virtualFile.output, "UTF-8"))
    writer.write(sourceCode)
    writer.close()
    val source = new SourceFile(virtualFile, Codec.UTF8)

    val start = source.lineToOffset(6)
    val end = source.lineToOffset(7) - 1
    val exprPos = Position(start, end)

    val rootCtx = (new ContextBase).initialCtx

    val settings = List("-usejavacp") ++ args.toList
    val runCtx: Context = DebugDriver.setup(settings.toArray, rootCtx)._2
    // ctx.initialize()(ctx)

    val compiler = new DebugCompiler(exprPos)
    val run = compiler.newRun(runCtx)

    run.compileSources(List(source))
    run.printSummary()

    implicit val ctx: Context = run.runContext

    val u = run.units.head.untpdTree
    val unAround = NavigateAST.pathTo(exprPos, u).asInstanceOf[List[untpd.Tree]].head
    val idents = collectIdents(unAround)
    println("idents: " + idents)



    val t = run.units.head.tpdTree


    idents.foreach { ident =>
      val tree = NavigateAST.pathTo(ident.pos, t).asInstanceOf[List[tpd.Tree]].head
      val defPos = tree.symbol.pos
      if (!(defPos.exists && exprPos.contains(defPos))) {
        val typerTpe = ctx.atPhase(ctx.typerPhase) { implicit ctx =>
          tree.denot.info
        }


        println(s"$ident became $tree with type $typerTpe")
      }
    } 
      // val path = NavigateAST.pathTo(pos, t).asInstanceOf[List[tpd.Tree]]
      // val around = path.head

      // val trees =
      //   if (pos.contains(around.pos))
      //     List(around)
      //   else {
      //     around.productIterator.toList.collect {
      //       case p: tpd.Tree if pos.contains(p.pos) => p
      //     }
      //   }

      // println("trees: " + trees.map(_.show))
 }
  def main2(args: Array[String]): Unit = {
    val vmm = Bootstrap.virtualMachineManager()
    val ac = vmm.attachingConnectors().get(0)
    val env = ac.defaultArguments()
    env.get("port").setValue("5005")
    env.get("hostname").setValue("localhost")
    val vm = ac.attach(env)

    // val threads = vm.allThreads
    // val mainThread = threads.asScala.find(_.name == "main").get
    // println(mainThread)

    val evtReqMgr = vm.eventRequestManager
    val cpr = evtReqMgr.createClassPrepareRequest
    cpr.addClassFilter("Test")
    cpr.setEnabled(true)

    vm.allClasses.asScala.find(_.name == "Test") match {
      case Some(testModule) =>
        println("PREP: " + testModule)
        val loc = testModule.locationsOfLine(7).get(0)

        val bReq = evtReqMgr.createBreakpointRequest(loc)
        bReq.setSuspendPolicy(EventRequest.SUSPEND_ALL)
        // bReq.setSuspendPolicy(BreakpointRequest.SUSPEND_ALL)
        bReq.enable()
      case _ =>
    }

    vm.resume()

    val evtQueue = vm.eventQueue
    // println("evt: " + evtQueue)
    while (true) {
      println("hi")
      val evtSet = evtQueue.remove()
      println("ho")
      val evtIter = evtSet.eventIterator
      while (evtIter.hasNext()) {
        val evt = evtIter.next()
        evt match {
          case evt: BreakpointEvent =>
            println("Breakpoint at line: " + evt.location.lineNumber)
            val threadRef = evt.thread
            val stackFrame = threadRef.frame(0)
            val visVars = stackFrame.visibleVariables
            println("vars: " + visVars)
            val testModule = vm.allClasses.asScala.find(_.name == "Test").get.asInstanceOf[ClassType]
            val exec = testModule.methodsByName("exec").get(0)
            testModule.invokeMethod(threadRef, exec, stackFrame.getValues(visVars).values.asScala.toList.asJava, 0)
          case evt: ClassPrepareEvent =>
            println("cpe: " + evt)
            // val testModule = vm.allClasses.asScala.find(_.name == "Test$").get
            val testModule = evt.referenceType
            println(testModule)
            val loc = testModule.locationsOfLine(9).get(0)

            val bReq = evtReqMgr.createBreakpointRequest(loc)
            bReq.setSuspendPolicy(EventRequest.SUSPEND_ALL)
            // bReq.setSuspendPolicy(BreakpointRequest.SUSPEND_ALL)
            bReq.enable()
            println("enab: " + bReq)
          case req if req != null =>
            println("other: " + req + " " + req.getClass)
          case _ =>
        }
      }
      evtSet.resume()
    }
  }
}
