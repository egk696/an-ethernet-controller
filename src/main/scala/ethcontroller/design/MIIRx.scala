// See README.md for license details.

package ethcontroller.design

import Chisel._
import ethcontroller.interfaces.MIIChannel
import ethcontroller.protocols.EthernetConstants
import ethcontroller.utils.{Deserializer, ExtClockSampler}

/**
  * The RX MII channel manages the reception of bytes from the PHY
  */
class MIIRx extends Module {
  val io = new Bundle() {
    val miiChannel = new MIIChannel().asInput
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
  val miiClkSampler = Module(new ExtClockSampler(true, 2))
  miiClkSampler.io.extClk := io.miiChannel.clk
  val miiDvSyncedReg = ShiftRegister(io.miiChannel.dv, 2)
  val miiDataSyncedReg = ShiftRegister(io.miiChannel.data, 2)
  val miiErrSyncedReg = ShiftRegister(io.miiChannel.err, 2)

  /**
    * Flags
    */
  val phyNA = io.miiChannel.col &&  !io.miiChannel.crs
  val phyError = phyNA || miiErrSyncedReg
  val validPHYData = miiDvSyncedReg && !miiErrSyncedReg && !phyNA
  validPHYDataReg := validPHYData
  val risingMIIEdge = miiClkSampler.io.sampledClk

  /**
    * Buffering PHY bytes
    */
  val deserializePHYByte = Module(new Deserializer(false, 4, 8))
  deserializePHYByte.io.en := io.rxEn & risingMIIEdge & validPHYData
  deserializePHYByte.io.clr := phyError || eofReg
  deserializePHYByte.io.shiftIn := miiDataSyncedReg
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
  when(validPHYDataReg && !validPHYData) {
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
