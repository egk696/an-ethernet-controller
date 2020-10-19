// See README.md for license details.

package ethcontroller.design

import Chisel._
import ethcontroller.interfaces.MIIChannel
import ethcontroller.protocols.EthernetConstants
import ocp._

/**
  * The RX channel of the Ethernet controller manages both
  * the RX MII channel and implements FIFO access to the connected buffers
  *
  * @param numFrameBuffers the numbers of buffers used
  * @param timeStampWidth  the timestamp bit-width
  */
class EthRxController(numFrameBuffers: Int, timeStampWidth: Int) extends Module {


  val io = IO(new Bundle() {
    val ocp = new OcpCoreSlavePort(32, 32)
    val ramPorts = Vec(numFrameBuffers, new OcpCoreMasterPort(32, 32))
    val miiChannel = new MIIChannel().asInput
    val rtcTimestamp = UInt(INPUT, width = timeStampWidth)
  })

  /**
    * Constants and types defs
    */
  val constRamIdWidth = log2Ceil(numFrameBuffers)
  val constRamAddrWidth = log2Ceil(EthernetConstants.constEthFrameLength)
  val stWaitSFD :: stCollectPayload :: Nil = Enum(UInt(), 2)
  val miiRx = Module(new MIIRx())

  /**
    * Registers
    */
  val stateReg = Reg(init = stWaitSFD)
  val ethByteReg = RegEnable(miiRx.io.ethByte, miiRx.io.ethByteDv)
  val ethByteDvReg = Reg(next = miiRx.io.ethByteDv)
  val ocpMasterReg = Reg(next = io.ocp.M)
  val wrRamIdReg = Reg(init = UInt(0, width = constRamIdWidth))
  val rdRamIdReg = Reg(init = UInt(0, width = constRamIdWidth))
  val byteCntReg = Reg(init = UInt(0, width = constRamAddrWidth))
  val ramMasterRegs = Vec.fill(numFrameBuffers) {
    Reg(new OcpCoreMasterSignals(constRamAddrWidth, 32))
  }
  val ocpDataReg = Reg(init = Bits(0, width = 32))
  val ocpRespReg = Reg(init = OcpResp.NULL)
  val rxEnReg = Reg(init = true.B)
  val rxFrameSizeReg = Reg(init = UInt(0, width = constRamAddrWidth)) //bytes
  val fifoCountReg = Reg(init = UInt(0, width = constRamIdWidth + 1))
  val fifoEmptyReg = Reg(init = true.B)
  val fifoFullReg = Reg(init = false.B)
  val fifoPopReg = Reg(init = false.B)
  val fifoPushReg = Reg(init = false.B)
  val macRxFilterReg = Reg(init = UInt(0, width = EthernetConstants.constEthMacLength))
  val ethRxCtrlRegMap = Map(
    "rxEn" -> Map("Reg" -> rxEnReg, "Addr" -> Bits("h00")),
    "frameSize" -> Map("Reg" -> rxFrameSizeReg, "Addr" -> Bits("h01")),
    "fifoCount" -> Map("Reg" -> fifoCountReg, "Addr" -> Bits("h02")),
    "fifoEmpty" -> Map("Reg" -> fifoEmptyReg, "Addr" -> Bits("h03")),
    "fifoFull" -> Map("Reg" -> fifoFullReg, "Addr" -> Bits("h04")),
    "fifoPop" -> Map("Reg" -> fifoPopReg, "Addr" -> Bits("h05")),
    "fifoPush" -> Map("Reg" -> fifoPushReg, "Addr" -> Bits("h06")),
    "macFilter" -> Map("Reg" -> macRxFilterReg, "Addr" -> Bits("h07"))
  )


  /**
    * Fifo Management
    */
  fifoEmptyReg := fifoCountReg === 0.U
  fifoFullReg := fifoCountReg === numFrameBuffers.U

  /**
    * State machine Control
    */
  val ramWrSelOH = UIntToOH(wrRamIdReg)
  val ramRdSelOH = UIntToOH(rdRamIdReg)

  val ocpSelRam = ~ocpMasterReg.Addr(constRamAddrWidth) && ocpMasterReg.Cmd =/= OcpCmd.IDLE
  val validRamPop = fifoPopReg && !fifoEmptyReg
  val validRamPush = fifoPushReg && !fifoFullReg

  val ethRamWrEn = ethByteDvReg && stateReg === stCollectPayload
  val ocpRamRdEn = ocpMasterReg.Cmd === OcpCmd.RD && ocpSelRam

  fifoPushReg := false.B
  fifoPopReg := false.B
  switch(stateReg) {
    is(stWaitSFD) {
      when(miiRx.io.ethSof && !fifoFullReg) {
        stateReg := stCollectPayload
      }
    }
    is(stCollectPayload) {
      when(miiRx.io.ethEof) {
        stateReg := stWaitSFD
        rxFrameSizeReg := byteCntReg
        byteCntReg := 0.U
        fifoPushReg := true.B
      }
    }
  }

  when(ethRamWrEn) {
    byteCntReg := byteCntReg + 1.U
  }


  when(validRamPop) {
    rdRamIdReg := rdRamIdReg + 1.U
    fifoCountReg := fifoCountReg - 1.U
  }

  when(validRamPush) {
    wrRamIdReg := wrRamIdReg + 1.U
    fifoCountReg := fifoCountReg + 1.U
  }

  /**
    * Ram Master Control
    */
  for (ramId <- 0 until numFrameBuffers) {
    ramMasterRegs(ramId).Addr := RegNext(Mux(ramId.U === wrRamIdReg && ocpSelRam, byteCntReg(constRamAddrWidth - 1, 2), ocpMasterReg.Addr(constRamAddrWidth - 1, 2)))
    ramMasterRegs(ramId).ByteEn := RegNext(Mux(ramId.U === wrRamIdReg && ocpSelRam, UIntToOH(byteCntReg(1, 0), width = 4), Mux(ocpRamRdEn, ocpMasterReg.ByteEn, Bits("b0000"))))
    ramMasterRegs(ramId).Cmd := RegNext(Mux(ramId.U === wrRamIdReg && ocpSelRam, OcpCmd.WR, Mux(ocpRamRdEn, OcpCmd.RD, OcpCmd.IDLE)))
    ramMasterRegs(ramId).Data := RegNext(ethByteReg)
  }

  /**
    * Regs Master Control
    */
  ocpRespReg := OcpResp.NULL
  when(ocpSelRam) {
    ocpDataReg := io.ramPorts(rdRamIdReg).S.Data
    ocpRespReg := Mux(!fifoFullReg, io.ramPorts(rdRamIdReg).S.Resp, OcpResp.FAIL)
  }.otherwise {
    when(ocpMasterReg.Cmd === OcpCmd.RD) {
      ocpRespReg := OcpResp.DVA
      when(ocpMasterReg.Addr(4, 2) === ethRxCtrlRegMap("rxEn")("Addr")) {
        ocpDataReg := ethRxCtrlRegMap("rxEn")("Reg")
      }.elsewhen(ocpMasterReg.Addr(4, 2) === ethRxCtrlRegMap("frameSize")("Addr")) {
        ocpDataReg := ethRxCtrlRegMap("frameSize")("Reg")
      }.elsewhen(ocpMasterReg.Addr(4, 2) === ethRxCtrlRegMap("fifoCount")("Addr")) {
        ocpDataReg := ethRxCtrlRegMap("fifoCount")("Reg")
      }.elsewhen(ocpMasterReg.Addr(4, 2) === ethRxCtrlRegMap("fifoEmpty")("Addr")) {
        ocpDataReg := ethRxCtrlRegMap("fifoEmpty")("Reg")
      }.elsewhen(ocpMasterReg.Addr(4, 2) === ethRxCtrlRegMap("fifoFull")("Addr")) {
        ocpDataReg := ethRxCtrlRegMap("fifoFull")("Reg")
      }.elsewhen(ocpMasterReg.Addr(4, 2) === ethRxCtrlRegMap("fifoPop")("Addr")) {
        ocpDataReg := ethRxCtrlRegMap("fifoPop")("Reg")
      }.elsewhen(ocpMasterReg.Addr(4, 2) === ethRxCtrlRegMap("fifoPush")("Addr")) {
        ocpDataReg := ethRxCtrlRegMap("fifoPush")("Reg")
      }.elsewhen(ocpMasterReg.Addr(4, 2) === ethRxCtrlRegMap("macFilter")("Addr")) {
        ocpDataReg := ethRxCtrlRegMap("macFilter")("Reg")
      }
    }.elsewhen(ocpMasterReg.Cmd === OcpCmd.WR) {
      when(ocpMasterReg.Addr(4, 2) === ethRxCtrlRegMap("rxEn")("Addr")) {
        ethRxCtrlRegMap("rxEn")("Reg") := ocpMasterReg.Data.orR
        ocpRespReg := OcpResp.DVA
      }.elsewhen(ocpMasterReg.Addr(4, 2) === ethRxCtrlRegMap("frameSize")("Addr")) {
        ocpRespReg := OcpResp.ERR
      }.elsewhen(ocpMasterReg.Addr(4, 2) === ethRxCtrlRegMap("fifoCount")("Addr")) {
        ocpRespReg := OcpResp.ERR
      }.elsewhen(ocpMasterReg.Addr(4, 2) === ethRxCtrlRegMap("fifoEmpty")("Addr")) {
        ocpRespReg := OcpResp.ERR
      }.elsewhen(ocpMasterReg.Addr(4, 2) === ethRxCtrlRegMap("fifoFull")("Addr")) {
        ocpRespReg := OcpResp.ERR
      }.elsewhen(ocpMasterReg.Addr(4, 2) === ethRxCtrlRegMap("fifoPop")("Addr")) {
        ethRxCtrlRegMap("fifoPop")("Reg") := true.B //set on write and reset in the next clock cycle
        ocpRespReg := OcpResp.DVA //is the responsibility of the master to check first the count ?
      }.elsewhen(ocpMasterReg.Addr(4, 2) === ethRxCtrlRegMap("fifoPush")("Addr")) {
        ocpRespReg := OcpResp.ERR
      }.elsewhen(ocpMasterReg.Addr(4, 2) === ethRxCtrlRegMap("macFilter")("Addr")) {
        ethRxCtrlRegMap("macFilter")("Reg") := ocpMasterReg.Data
        ocpRespReg := OcpResp.DVA
      }
    }.otherwise {
      ocpRespReg := OcpResp.NULL
    }
  }

  /**
    * I/O plumbing
    */
  io.ocp.S.Data := ocpDataReg
  io.ocp.S.Resp := ocpRespReg
  miiRx.io.miiChannel <> io.miiChannel
  miiRx.io.rxEn := ethRxCtrlRegMap("rxEn")("Reg")
  for (ramId <- 0 until numFrameBuffers) {
    io.ramPorts(ramId).M := ramMasterRegs(ramId)
  }
}


object EthRxController extends App {
  chisel3.Driver.execute(Array("--target-dir", "generated"), () => new EthRxController(2, 64))
}