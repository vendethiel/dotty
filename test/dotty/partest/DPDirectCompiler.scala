package dotty.partest

import dotty.tools.dotc.reporting.ConsoleReporter
import scala.tools.partest.{ TestState, nest }
import java.io.{ File, PrintStream, FileOutputStream }


/* NOTE: Adapted from partest.DirectCompiler and DottyTest */
class DPDirectCompiler(runner: DPTestRunner) extends nest.DirectCompiler(runner) {

  override def compile(opts0: List[String], sources: List[File]): TestState = {
    val clogStream = new PrintStream(new FileOutputStream(runner.cLogFile.jfile), true)
    clogStream.println("\ncompiling " + sources.mkString(" ") + "\noptions: " + opts0.mkString(" "))

    implicit val ctx: dotty.tools.dotc.core.Contexts.Context = {
      val base = new dotty.tools.dotc.core.Contexts.ContextBase
      import base.settings._
      val ctx = base.initialCtx.fresh.setSetting(printtypes, true)
        .setSetting(pageWidth, 90).setSetting(log, List("<some"))
      base.definitions.init(ctx)
      ctx
    }

    val savedOut = System.out
    val savedErr = System.err
    val processor =
      if (opts0.exists(_.startsWith("#"))) dotty.tools.dotc.Bench else dotty.tools.dotc.Main

    try {
      System.setOut(clogStream)
      System.setErr(clogStream)
      val reporter = processor.process((sources.map(_.toString) ::: opts0).toArray)
      if (!reporter.hasErrors) runner.genPass()
      else {
        reporter.printSummary(ctx)
        runner.genFail(s"compilation failed with ${reporter.errorCount} errors")
      }
    } catch {
      case t: Throwable =>
        t.printStackTrace(savedErr)
        t.printStackTrace(clogStream)
        runner.genCrash(t)
    } finally {
      System.setOut(savedOut)
      System.setErr(savedErr)
      clogStream.close
    }
  }
}
