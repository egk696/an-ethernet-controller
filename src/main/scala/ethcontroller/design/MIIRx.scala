// See README.md for license details.

package ethcontroller.design

import Chisel._
import ethcontroller.interfaces.MIIChannel
import ethcontroller.protocols.EthernetConstants
import ethcontroller.utils.Deserializer

class MIIRx extends Module {
  val io = new Bundle() {
    val miiChannel = new MIIChannel().asInput()
    val rxEn = Bool(INPUT)
    val phyErr = Bool(OUTPUT)
    val ethSof = Bool(OUTPUT)
    val ethEof = Bool(OUTPUT)
    val ethByte = Bits(OUTPUT, width = 8)
    val ethByteDv = Bool(OUTPUT)
  }

  /**
    * Registers
    */
  val sofReg = Reg(init = Bool(false))
  val eofReg = Reg(init = Bool(false))
  val validPHYDataReg = Reg(init = Bool(false))

  /**
    * Sampling clock and line of MII
    */
  val miiClkReg = Reg(next = io.miiChannel.clk)
  val miiClkReg2 = Reg(next = miiClkReg)
  val miiDvReg = Reg(next = io.miiChannel.dv)
  val miiDvReg2 = Reg(next = miiDvReg)
  val miiDataReg = Reg(next = io.miiChannel.data)
  val miiDataReg2 = Reg(next = miiDataReg)
  val miiErrReg = Reg(next = io.miiChannel.err)
  val miiErrReg2 = Reg(next = miiErrReg)

  /**
    * Flags
    */
  val phyNA = io.miiChannel.col & ~io.miiChannel.crs
  val phyError = phyNA || miiErrReg2
  val validPHYData = miiDvReg2 & ~miiErrReg2 & ~phyNA
  validPHYDataReg := validPHYData
  val risingMIIEdge = miiClkReg & ~miiClkReg2

  /**
    * Buffering PHY bytes
    */
  val deserializePHYByte = Module(new Deserializer(false, 4, 8))
  deserializePHYByte.io.en := io.rxEn & risingMIIEdge & validPHYData
  deserializePHYByte.io.clr := phyError || eofReg
  deserializePHYByte.io.shiftIn := miiDataReg2
  val byteReg = Reg(init = Bits(0, width = 8), next = deserializePHYByte.io.shiftOut)
  val wrByteReg = Reg(init = Bool(false), next = deserializePHYByte.io.done)

  /**
    * Buffer & re-order PHY double-words
    */
  val deserializePHYBuffer = Module(new Deserializer(true, 8, 64))
  deserializePHYBuffer.io.en := deserializePHYByte.io.done
  deserializePHYBuffer.io.clr := phyError || eofReg
  deserializePHYBuffer.io.shiftIn := deserializePHYByte.io.shiftOut
  val regBuffer = Reg(init = Bits(0, width = 64), next = deserializePHYBuffer.io.shiftOut)
  val regBufferDv = Reg(init = Bool(false), next = deserializePHYBuffer.io.done)

  /**
    * Ethernet frame detection logic
    */
  sofReg := false.B
  when(regBuffer === EthernetConstants.constSFD && regBufferDv) {
    sofReg := true.B
  }

  eofReg := false.B
  when(validPHYDataReg && ~validPHYData) {
    eofReg := true.B
  }

  /**
    * I/O plumbing
    */
  io.phyErr := phyNA
  io.ethSof := sofReg
  io.ethEof := eofReg
  io.ethByte := byteReg
  io.ethByteDv := wrByteReg
}
