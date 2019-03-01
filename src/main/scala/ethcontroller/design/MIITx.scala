package ethcontroller.design

import Chisel._
import ethcontroller.interfaces.MIIChannel
import ethcontroller.utils.Serializer

class MIITx extends Module {
  val io = new Bundle() {
    val miiChannel = new MIIChannel()
    val startTx = Bool(INPUT)
    val endTx = Bool(INPUT)
    val busy = Bool(OUTPUT)
    val macDataDv = Bool(INPUT)
    val macData = Bits(INPUT, width = 64)
    val phyErr = Bool(OUTPUT)
  }

  /**
    * Registers and components
    */
  val busyReg = Reg(init = false.B)
  val startTxReg = Reg(init = false.B)

  val serializeDataToByte = Module(new Serializer(false, 64, 8))
  val serializeByteToNibble = Module(new Serializer(false, 8, 4))

  /**
    * Sampling clock and line of MII
    */
  val miiClkReg = Reg(next = io.miiChannel.clk)
  val miiClkReg2 = Reg(next = miiClkReg)
  val miiErrReg = Reg(next = io.miiChannel.err)
  val miiErrReg2 = Reg(next = miiErrReg)
  val miiDataReg = Reg(init = UInt(0, width = 4))
  val miiDvReg = Reg(init = false.B)

  /**
    * Flags
    */
  val phyNA = io.miiChannel.col & ~io.miiChannel.crs
  val phyError = phyNA || miiErrReg2
  val fallingMIIEdge = ~miiClkReg & miiClkReg2 //we check the falling so that when the rising arrives on the next rising the data will be available to the PHY


  /**
    * Serialize Preamble to Byte
    */
  serializeDataToByte.io.load := io.macDataDv
  serializeDataToByte.io.shiftIn := io.macData
  serializeDataToByte.io.en := ~serializeByteToNibble.io.dv

  /**
    * Serialize Output to Nibble
    */
  serializeByteToNibble.io.load := serializeDataToByte.io.dv
  serializeByteToNibble.io.shiftIn := serializeDataToByte.io.shiftOut
  serializeByteToNibble.io.en := fallingMIIEdge

  /**
    * Interface control and automatic PREAMBLE/IFG injection
    */
  when(io.startTx) {
    busyReg := true.B
  }.elsewhen(RegNext(serializeDataToByte.io.done) && RegNext(serializeByteToNibble.io.done)) {
    busyReg := false.B
  }

  when(fallingMIIEdge) {
    //(De-)Assert MII data valid
    when(busyReg) {
      miiDvReg := true.B
    }.elsewhen(~busyReg) {
      miiDvReg := false.B
    }
    //Data nibble
    miiDataReg := serializeByteToNibble.io.shiftOut
  }

  /**
    * I/O plumbing
    */
  io.busy := serializeDataToByte.io.dv
  io.phyErr := phyNA
  io.miiChannel.data := miiDataReg
  io.miiChannel.dv := miiDvReg

}
