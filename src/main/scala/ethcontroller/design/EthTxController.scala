// See README.md for license details.

package ethcontroller.design

import Chisel._
import ethcontroller.interfaces.MIIChannel
import ethcontroller.protocols.EthernetConstants
import ocp._

/**
  * The TX channel of the Ethernet controller manages both
  * the TX MII channel and implements FIFO access to the connected buffers
  *
  * @param numFrameBuffers the numbers of buffers used
  * @param timeStampWidth the timestamp bit-width
  */
class EthTxController(numFrameBuffers: Int, timeStampWidth: Int) {
  val io = new Bundle() {
    val ocp = new OcpCoreSlavePort(32, 32)
    val ramPorts = Vec.fill(numFrameBuffers) {
      new OcpCoreMasterPort(32, 32)
    }
    val miiChannel = new MIIChannel()
    val rtcTimestamp = UInt(INPUT, width = timeStampWidth)
  }

  /**
    * Constants and types defs
    */
  val constRamIdWidth = log2Ceil(numFrameBuffers)
  val constRamAddrWidth = log2Ceil(EthernetConstants.constEthFrameLength)
  val sWaitSFD :: stCollectPayload :: Nil = Enum(UInt(), 2)
  val miiTx = Module(new MIITx())

  /**
    * Registers
    */
  val stateReg = Reg(init = sWaitSFD)
  val ethByteReg = RegEnable(miiRx.io.ethByte, miiRx.io.ethByteDv)
  val ethByteDvReg = Reg(next = miiRx.io.ethByteDv)
  val ocpMasterReg = Reg(next = io.ocp.M)
  val wrRamIdReg = Reg(init = UInt(0, width = constRamIdWidth))
  val rdRamIdReg = Reg(init = UInt(0, width = constRamIdWidth))
  val byteCntReg = Reg(init = UInt(0, width = constRamAddrWidth))
  val ramMasterRegs = Vec.fill(numFrameBuffers) {
    Reg(new OcpCoreMasterSignals(constRamAddrWidth, 32))
  }
  val dataReg = Reg(init = Bits(0, width = 32))
  val respReg = Reg(init = OcpResp.NULL)

  val rxEnReg = Reg(init = Bool(true))
  val fifoCountReg = Reg(init = UInt(0, width = constRamIdWidth + 1))
  val fifoEmptyReg = Reg(init = Bool(true))
  val fifoFullReg = Reg(init = Bool(false))
  val fifoPopReg = Reg(init = Bool(false))
  val macRxFilterReg = Reg(init = UInt(0, width = EthernetConstants.constEthMacLength))

  val ethRxCtrlRegMap = Map(
    "rxEn" -> Map("Reg" -> rxEnReg, "Addr" -> Bits("h00")),
    "fifoCount" -> Map("Reg" -> fifoCountReg, "Addr" -> Bits("h01")),
    "fifoEmpty" -> Map("Reg" -> fifoEmptyReg, "Addr" -> Bits("h02")),
    "fifoFull" -> Map("Reg" -> fifoFullReg, "Addr" -> Bits("h03")),
    "fifoPop" -> Map("Reg" -> fifoPopReg, "Addr" -> Bits("h04")),
    "macRxFilter" -> Map("Reg" -> macRxFilterReg, "Addr" -> Bits("h05"))
  )

  /**
    * Fifo Management
    */
  fifoEmptyReg := fifoCountReg === 0.U
  fifoFullReg := fifoCountReg === numFrameBuffers.U
}
