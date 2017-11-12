package dotty.tools
package dotc
package interactive

import core._
import Phases._
import typer._
import Contexts._

class DebugCompiler extends Compiler {
  // Stop before backend
  override def phases: List[List[Phase]] = super.phases.init
}
