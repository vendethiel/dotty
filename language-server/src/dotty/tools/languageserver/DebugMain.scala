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
  Phase 1a: Extract expr to companion with $this and non-local idents as params
           //~original becomes forwarder~(not actually needed)
  Phase 1b: Extract methods in expr in typer
  Phase 2: Get rid of all trees not in pos of expr, keep forwarder call around
           //Easy mode: get rid of everything but Global
  Phase 3: Create entry point from forwarder call:
    this -> thisParam
    local x -> locals("x")
    this.a.b -> thisWithRefl.a.b
  */

object DebugMain {
  val sourceCode =
"""
package dbg

import scala.collection.immutable._

class Foo {
  val foo: String = "duck"
  def test(bar: String): Unit = {
    val bla: String = "hehe"

    { def x = 1; (); println(foo + bar + bla + x) }
    // FIXME: block might not survive until backend, more reliable solution ?
  }
}

object Global {
  def makeMap(names: Array[String], locals: Array[Object]): Map[String, Object] =
    (names, locals).zipped.toMap
}
"""

  def main(args: Array[String]): Unit = {
    val virtualFile = new VirtualFile("<virtual>")
    val writer = new BufferedWriter(new OutputStreamWriter(virtualFile.output, "UTF-8"))
    writer.write(sourceCode)
    writer.close()
    val source = new SourceFile(virtualFile, Codec.UTF8)

    val start = source.lineToOffset(10)
    val end = source.lineToOffset(11) - 1
    val exprPos = Position(start, end)

    val rootCtx = (new ContextBase).initialCtx

    val settings = List("-usejavacp") ++ args.toList
    val runCtx: Context = DebugDriver.setup(settings.toArray, rootCtx)._2
    // ctx.initialize()(ctx)

    val compiler = new DebugCompiler(exprPos)
    val run = compiler.newRun(runCtx)

    run.compileSources(List(source))
    run.printSummary()

    implicit val ctx = run.runContext

    val t = run.units.head.tpdTree
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

    vm.classesByName("Test").asScala.headOption match {
      case Some(testModule) =>
        println("PREP: " + testModule)
        val loc = testModule.locationsOfLine(9).get(0)

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
            val testModule = vm.classesByName("Test").asScala.head.asInstanceOf[ClassType]

            val stringClass = vm.classesByName("java.lang.String").asScala.head.asInstanceOf[ClassType]

            val stringArrayType = vm.classesByName("java.lang.String[]").asScala.head.asInstanceOf[ArrayType]
            val objArrayType = vm.classesByName("java.lang.Object[]").asScala.head.asInstanceOf[ArrayType]

            val nameArrayRef = stringArrayType.newInstance(visVars.size)
            val localsArrayRef = objArrayType.newInstance(visVars.size)
            nameArrayRef.setValues(visVars.asScala.map(v => vm.mirrorOf(v.name)).asJava)
            localsArrayRef.setValues(visVars.asScala.map(stackFrame.getValue).asJava)

            val exec = testModule.methodsByName("exec").get(0)
            testModule.invokeMethod(threadRef, exec, List(stackFrame.thisObject, nameArrayRef, localsArrayRef).asJava, 0)
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
