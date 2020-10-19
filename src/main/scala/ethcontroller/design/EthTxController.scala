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
  * @param timeStampWidth  the timestamp bit-width
  */
class EthTxController(numFrameBuffers: Int, timeStampWidth: Int) extends Module {
  val io = new Bundle() {
    val ocp = new OcpCoreSlavePort(32, 32)
    val ramPorts = Vec(numFrameBuffers, new OcpCoreMasterPort(32, 32))
    val miiChannel = new MIIChannel()
    val rtcTimestamp = UInt(INPUT, width = timeStampWidth)
  }


  /**
    * Constants and types defs
    */
  val constRamIdWidth = log2Ceil(numFrameBuffers)
  val constRamAddrWidth = log2Ceil(EthernetConstants.constEthFrameLength)
  val stWait :: stTxPreamble :: stTxFrame :: stTxFCS :: stIFG :: stEoF :: Nil = Enum(UInt(), 6)
  val miiTx = Module(new MIITx())

  /**
    * Registers
    */
  val stateReg = Reg(init = stWait)
  val ocpMasterReg = Reg(next = io.ocp.M)
  val wrRamIdReg = Reg(init = UInt(0, width = constRamIdWidth))
  val rdRamIdReg = Reg(init = UInt(0, width = constRamIdWidth))
  val byteCntReg = Reg(init = UInt(0, width = constRamAddrWidth))
  val ethRamRdEn = Reg(init = false.B)
  val ramMasterRegs = Vec.fill(numFrameBuffers) {
    Reg(new OcpCoreMasterSignals(constRamAddrWidth, 32))
  }
  val ocpDataReg = Reg(init = Bits(0, width = 32))
  val ocpRespReg = Reg(init = OcpResp.NULL)

  val ramDataReg = Reg(init = Bits(0, width = 32))
  val ramRespReg = Reg(init = OcpResp.NULL)

  val txEnReg = Reg(init = true.B)
  val txFrameSizeReg = Reg(init = UInt(0, width = constRamAddrWidth)) //bytes
  val fifoCountReg = Reg(init = UInt(0, width = constRamIdWidth + 1))
  val fifoEmptyReg = Reg(init = true.B)
  val fifoFullReg = Reg(init = false.B)
  val fifoPopReg = Reg(init = false.B)
  val fifoPushReg = Reg(init = false.B)
  val macRxFilterReg = Reg(init = UInt(0, width = EthernetConstants.constEthMacLength))

  val ethTxCtrlRegMap = Map(
    "txEn" -> Map("Reg" -> txEnReg, "Addr" -> Bits("h00")), //0x0
    "frameSize" -> Map("Reg" -> txFrameSizeReg, "Addr" -> Bits("h04")), //0x4
    "fifoCount" -> Map("Reg" -> fifoCountReg, "Addr" -> Bits("h08")), //0x8
    "fifoEmpty" -> Map("Reg" -> fifoEmptyReg, "Addr" -> Bits("h0C")), //0xC
    "fifoFull" -> Map("Reg" -> fifoFullReg, "Addr" -> Bits("h10")), //0x10
    "fifoPop" -> Map("Reg" -> fifoPopReg, "Addr" -> Bits("h14")), //0x14
    "fifoPush" -> Map("Reg" -> fifoPushReg, "Addr" -> Bits("h18")), //0x18
    "macFilter" -> Map("Reg" -> macRxFilterReg, "Addr" -> Bits("h1C")) //0x1C
  )

  //val preambleReg = Reg(init = EthernetConstants.constSFD)
  val constSFDbytes = 64/8
  val preambleReg = Reg(Vec(constSFDbytes,UInt(8.W)))
  for(i <- 0 to constSFDbytes-1)
  {
    preambleReg(i) := EthernetConstants.constSFD(8+8*i-1,i*8)
  }
  //val fcsReg = Reg(init = UInt(0, width = 16))
  val fcsReg = Reg(Vec(2, UInt(8.W)))

  val miiByteDataReg = Reg(init = UInt(0, width = 8))
  val miiByteLoadReg = Reg(init = false.B)
  val miiIsReady = Wire(init = false.B)

  /**
    * Fifo Management
    */
  fifoEmptyReg := fifoCountReg === 0.U
  fifoFullReg := fifoCountReg === numFrameBuffers.U

  val ocpSelRam = ~ocpMasterReg.Addr(constRamAddrWidth) && ocpMasterReg.Cmd =/= OcpCmd.IDLE
  val validRamPop = fifoPopReg === true.B && !fifoEmptyReg
  val validRamPush = fifoPushReg === true.B && !fifoFullReg

  //Reset regs to default values
  fifoPushReg := false.B
  fifoPopReg := false.B
  miiByteLoadReg := false.B
  //State Machine
  switch(stateReg) {
    is(stWait) { //wait for available data in FIFORam
      when(!fifoEmptyReg) {
        stateReg := stTxPreamble
      }
    }
    is(stTxPreamble) {
      when(miiIsReady & ~miiByteLoadReg) { //wait until miiIsReady and then countup the PREAMBLE
        when(byteCntReg < 8.U) {
          miiByteLoadReg := true.B
          byteCntReg := byteCntReg + 1.U
        }.otherwise {
          byteCntReg := 0.U
          ethRamRdEn := true.B
          stateReg := stTxFrame
        }
      }
    }
    is(stTxFrame) {
      when(ramRespReg === OcpResp.DVA) {
        when(miiIsReady) {
          when(byteCntReg < txFrameSizeReg) {
            byteCntReg := byteCntReg + 1.U
          }.otherwise {
            byteCntReg := 0.U
            stateReg := stTxFrame
          }
        }
      }
    }
    is(stTxFCS) {
      when(miiIsReady) {
        when(byteCntReg < 4.U) {
          byteCntReg := byteCntReg + 1.U
        }.otherwise {
          byteCntReg := 0.U
          ethRamRdEn := false.B
          stateReg := stEoF
        }
      }
    }
    is(stEoF) {
      stateReg := stIFG
    }
    is(stIFG) {
      when(byteCntReg < 24.U) {
        byteCntReg := byteCntReg + 1.U
      }.otherwise {
        byteCntReg := 0.U
        stateReg := stWait
        fifoPopReg := true.B
      }
    }
  }

  when(validRamPush) {
    wrRamIdReg := wrRamIdReg + 1.U
    fifoCountReg := fifoCountReg + 1.U
  }

  when(validRamPop) {
    rdRamIdReg := rdRamIdReg + 1.U
    fifoCountReg := fifoCountReg - 1.U
  }


  /**
    * Multiplexing MII byte source
    */


  miiByteDataReg := RegNext(
                          Mux(stateReg === stTxPreamble, preambleReg(byteCntReg),
                                Mux(stateReg === stTxFCS, fcsReg(byteCntReg), ramDataReg)))


  /**
    * Ram master-port Control
    */
  for (ramId <- 0 until numFrameBuffers) {
    ramMasterRegs(ramId).Cmd := Mux(ramId.U === wrRamIdReg && ocpSelRam, ocpMasterReg.Cmd, Mux(ethRamRdEn, OcpCmd.RD, OcpCmd.IDLE))
    ramMasterRegs(ramId).Addr := Mux(ramId.U === wrRamIdReg && ocpSelRam, ocpMasterReg.Addr(constRamAddrWidth - 1, 2), byteCntReg(constRamAddrWidth - 1, 2))
    ramMasterRegs(ramId).ByteEn := Mux(ramId.U === wrRamIdReg && ocpSelRam, ocpMasterReg.ByteEn, UIntToOH(byteCntReg(1, 0), width = 4))
    ramMasterRegs(ramId).Data := ocpMasterReg.Data
  }

  /**
    * Ram slave-port Control
    */
  ramDataReg := io.ramPorts(rdRamIdReg).S.Data
  ramRespReg := io.ramPorts(rdRamIdReg).S.Resp

  /**
    * Regs Master Control
    */
  ocpRespReg := OcpResp.NULL
  when(ocpSelRam) {
    ocpDataReg := io.ramPorts(wrRamIdReg).S.Data
    ocpRespReg := Mux(!fifoFullReg, io.ramPorts(wrRamIdReg).S.Resp, OcpResp.FAIL)
  }.otherwise {
    when(ocpMasterReg.Cmd === OcpCmd.RD) {
      ocpRespReg := OcpResp.DVA
      when(ocpMasterReg.Addr(5, 0) === ethTxCtrlRegMap("txEn")("Addr")) {
        ocpDataReg := ethTxCtrlRegMap("txEn")("Reg")
      }.elsewhen(ocpMasterReg.Addr(5, 0) === ethTxCtrlRegMap("frameSize")("Addr")) {
        ocpDataReg := ethTxCtrlRegMap("frameSize")("Reg")
      }.elsewhen(ocpMasterReg.Addr(5, 0) === ethTxCtrlRegMap("fifoCount")("Addr")) {
        ocpDataReg := ethTxCtrlRegMap("fifoCount")("Reg")
      }.elsewhen(ocpMasterReg.Addr(5, 0) === ethTxCtrlRegMap("fifoEmpty")("Addr")) {
        ocpDataReg := ethTxCtrlRegMap("fifoEmpty")("Reg")
      }.elsewhen(ocpMasterReg.Addr(5, 0) === ethTxCtrlRegMap("fifoFull")("Addr")) {
        ocpDataReg := ethTxCtrlRegMap("fifoFull")("Reg")
      }.elsewhen(ocpMasterReg.Addr(5, 0) === ethTxCtrlRegMap("fifoPop")("Addr")) {
        ocpDataReg := ethTxCtrlRegMap("fifoPop")("Reg")
      }.elsewhen(ocpMasterReg.Addr(5, 0) === ethTxCtrlRegMap("fifoPush")("Addr")) {
        ocpDataReg := ethTxCtrlRegMap("fifoPush")("Reg")
      }.elsewhen(ocpMasterReg.Addr(5, 0) === ethTxCtrlRegMap("macFilter")("Addr")) {
        ocpDataReg := ethTxCtrlRegMap("macFilter")("Reg")
      }
    }.elsewhen(ocpMasterReg.Cmd === OcpCmd.WR) {
      when(ocpMasterReg.Addr(5, 0) === ethTxCtrlRegMap("txEn")("Addr")) {
        ethTxCtrlRegMap("txEn")("Reg") := ocpMasterReg.Data.orR()
        ocpRespReg := OcpResp.DVA
      }.elsewhen(ocpMasterReg.Addr(5, 0) === ethTxCtrlRegMap("frameSize")("Addr")) {
        ethTxCtrlRegMap("frameSize")("Reg") := ocpDataReg
        ocpRespReg := OcpResp.DVA
      }.elsewhen(ocpMasterReg.Addr(5, 0) === ethTxCtrlRegMap("fifoCount")("Addr")) {
        ocpRespReg := OcpResp.ERR
      }.elsewhen(ocpMasterReg.Addr(5, 0) === ethTxCtrlRegMap("fifoEmpty")("Addr")) {
        ocpRespReg := OcpResp.ERR
      }.elsewhen(ocpMasterReg.Addr(5, 0) === ethTxCtrlRegMap("fifoFull")("Addr")) {
        ocpRespReg := OcpResp.ERR
      }.elsewhen(ocpMasterReg.Addr(5, 0) === ethTxCtrlRegMap("fifoPop")("Addr")) {
        ocpRespReg := OcpResp.ERR
      }.elsewhen(ocpMasterReg.Addr(5, 0) === ethTxCtrlRegMap("fifoPush")("Addr")) {
        ethTxCtrlRegMap("fifoPush")("Reg") := ocpMasterReg.Data.orR() //set on write and reset in the next clock cycle
        ocpRespReg := OcpResp.DVA //is the responsibility of the master to check first the count ?
      }.elsewhen(ocpMasterReg.Addr(5, 0) === ethTxCtrlRegMap("macFilter")("Addr")) {
        ethTxCtrlRegMap("macFilter")("Reg") := ocpMasterReg.Data
        ocpRespReg := OcpResp.DVA
      }
    }.otherwise {
      ocpRespReg := OcpResp.NULL
    }
  }

  /**
    * IO Plumbing
    */
  io.ocp.S.Data := ocpDataReg
  io.ocp.S.Resp := ocpRespReg
  miiIsReady := miiTx.io.ready
  miiTx.io.macDataDv := miiByteLoadReg
  miiTx.io.macData := miiByteDataReg
  io.miiChannel <> miiTx.io.miiChannel
  for (ramId <- 0 until numFrameBuffers) {
    io.ramPorts(ramId).M := ramMasterRegs(ramId)
  }
}


object EthTxController extends App {
  chisel3.Driver.execute(Array("--target-dir", "generated"), () => new EthTxController(2, 64))
}