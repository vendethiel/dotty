package dotty.tools
package languageserver

import com.microsoft.java.debug.core._
import com.microsoft.java.debug.core.adapter._

import com.sun.jdi.{ Bootstrap, VirtualMachineManager }

class DottyVirtualMachineManagerProvider extends IVirtualMachineManagerProvider {
  def getVirtualMachineManager = Bootstrap.virtualMachineManager
}
