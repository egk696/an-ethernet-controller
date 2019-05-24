// See README.md for license details.

package ethcontroller.utils

import Chisel._

/**
  * Synchronizes external clock using a flip-flop synchronizer chain and samples the
  * requested external clock edge
  *
  * @param sampleRisingEdge if true it samples the rising edge, else the falling edge
  * @param syncChainDepth   the number of FF synchronizers to use
  */
class ExtClockSampler(sampleRisingEdge: Boolean = true, syncChainDepth: Int = 2) extends Module {
  val io = new Bundle() {
    val extClk = Bool(INPUT)
    val sampledClk = Bool(OUTPUT)
  }

  val extClkEdge = Wire(init = false.B) //this will be our sampled edge

  val extClkSyncedReg = ShiftRegister(io.extClk, syncChainDepth)
  val extClkSyncedOldReg = Reg(next = extClkSyncedReg)

  if (sampleRisingEdge) {
    extClkEdge := extClkSyncedReg & ~extClkSyncedOldReg //check for a rising edge
  } else {
    extClkEdge := ~extClkSyncedReg & extClkSyncedOldReg //or check for a falling edge
  }

  io.sampledClk := extClkEdge
}
