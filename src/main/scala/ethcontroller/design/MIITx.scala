package ethcontroller.design

import Chisel._
import ethcontroller.interfaces.MIIChannel
import ethcontroller.utils.{ExtClockSampler, Serializer}

/**
  * The TX MII channel manages the reception of bytes from the PHY
  */
class MIITx extends Module {
  val io = new Bundle() {
    val miiChannel = new MIIChannel()
    val macDataDv = Bool(INPUT)
    val macData = Bits(INPUT, width = 8)
    val ready = Bool(OUTPUT)
    val phyErr = Bool(OUTPUT)
  }

  /**
    * Registers and components
    */
  val transmittingReg = Reg(init = false.B)

  /**
    * Sampling clock and line of MII
    */
  val miiClkSampler = Module(new ExtClockSampler(true, 2))
  miiClkSampler.io.extClk := io.miiChannel.clk
  val miiErrSyncedReg = ShiftRegister(io.miiChannel.err, 2)
  val miiDataReg = Reg(init = UInt(0, width = 4))
  val miiDvReg = Reg(init = false.B)

  /**
    * Flags
    */
  val phyNA = io.miiChannel.col && !io.miiChannel.crs
  val phyError = phyNA || miiErrSyncedReg
  val miiClkEdge = miiClkSampler.io.sampledClk

  /**
    * Serialize Output to Nibble
    */
  val serializeByteToNibble = Module(new Serializer(false, 8, 4))
  serializeByteToNibble.io.load := io.macDataDv
  serializeByteToNibble.io.shiftIn := io.macData
  serializeByteToNibble.io.en := miiClkEdge

  when(!transmittingReg && serializeByteToNibble.io.dv) {
    transmittingReg := true.B
  }.elsewhen(!serializeByteToNibble.io.dv && serializeByteToNibble.io.done) {
    transmittingReg := false.B
  }

  /**
    * Place data on the falling MII clock edge so that they are available on the rising edge
    */
  when(miiClkEdge) {
    //(De-)Assert MII data valid
    when(transmittingReg) {
      miiDvReg := true.B
    }.elsewhen(!transmittingReg) {
      miiDvReg := false.B
    }
    //Data nibble
    miiDataReg := serializeByteToNibble.io.shiftOut
  }

  /**
    * I/O plumbing
    */
  io.ready := !transmittingReg || !serializeByteToNibble.io.dv
  io.miiChannel.data := miiDataReg
  io.miiChannel.dv := miiDvReg
  io.phyErr := phyNA

}
