package dotty.tools
package repl

import dotc.typer.FrontEnd
import dotc.CompilationUnit
import dotc.core.Contexts.Context

/** A customized `FrontEnd` for the REPL
 *
 *  This customized front end does not perform parsing as part of its `runOn`
 *  method. This allows us to keep the parsing separate from the rest of the
 *  compiler pipeline.
 */
private[repl] class REPLFrontEnd extends FrontEnd {
  override def phaseName = "frontend"

  override def isRunnable(implicit ctx: Context) = true

  override def runOn(units: List[CompilationUnit])(implicit ctx: Context) = {
    assert(units.size == 1) // REPl runs one compilation unit at a time

    val unitContext = ctx.fresh.setCompilationUnit(units.head)
    enterSyms(unitContext)
    enterAnnotations(unitContext)
    typeCheck(unitContext)
    List(unitContext.compilationUnit)
  }
}
