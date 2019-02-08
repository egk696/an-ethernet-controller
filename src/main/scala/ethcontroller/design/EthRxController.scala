package ethcontroller.design

import Chisel._
import ethcontroller.interfaces.MIIChannel
import ethcontroller.protocols.EthernetConstants
import ocp._

class EthRxController(numFrameBuffers: Int, timeStampWidth: Int) extends Module {
  val io = new Bundle(){
    val ocp = new OcpCoreSlavePort(32, 32)
    val ramPorts = Vec.fill(numFrameBuffers){new OcpCoreMasterPort(32, 32)}
    val miiChannel = new MIIChannel().asInput()
    val rtcTimestamp = UInt(INPUT, width = timeStampWidth)
  }

  /**
    * Constants and types defs
    */
  val constRamIdWidth = log2Ceil(numFrameBuffers)
  val constRamAddrWidth = log2Ceil(EthernetConstants.constEthFrameLength)
  val sWaitSFD :: stCollectPayload :: Nil = Enum(UInt(), 2)
  val miiRx = Module(new MIIRx())

  /**
    * Registers and devices insta
    */
  val stateReg = Reg(init = sWaitSFD)
  val ethByteReg = RegEnable(miiRx.io.ethByte, miiRx.io.ethByteDv)
  val ethByteDvReg = Reg(next = miiRx.io.ethByteDv)
  val ocpMasterReg = Reg(next = io.ocp.M)
  val wrRamIdReg = Reg(init = UInt(0, width = constRamIdWidth))
  val rdRamIdReg = Reg(init = UInt(0, width = constRamIdWidth))
  val byteCntReg = Reg(init = UInt(0, width = constRamAddrWidth))
  val ramMasterRegs = Vec.fill(numFrameBuffers){Reg(new OcpCoreMasterSignals(constRamAddrWidth, 32))}
  val dataReg = Reg(init = Bits(0, width = 32))
  val respReg = Reg(init = OcpResp.NULL)

  val rxEnReg = Reg(init = Bool(true))
  val fifoCountReg = Reg(init = UInt(0, width = constRamIdWidth+1))
  val fifoEmptyReg = Reg(init = Bool(true))
  val fifoFullReg = Reg(init = Bool(false))
  val fifoPopReg = Reg(init = Bool(false))
  val macRxFilterReg = Reg(init = UInt(0, width = EthernetConstants.constEthMacLength))

  val ethRxCtrlRegMap = Map(
    "rxEn"->Map("Reg"->rxEnReg, "Addr"->Bits("h00")),
    "fifoCount"->Map("Reg"->fifoCountReg, "Addr"->Bits("h01")),
    "fifoEmpty"->Map("Reg"->fifoEmptyReg, "Addr"->Bits("h02")),
    "fifoFull"->Map("Reg"->fifoFullReg, "Addr"->Bits("h03")),
    "fifoPop"->Map("Reg"->fifoPopReg, "Addr"->Bits("h04")),
    "macRxFilter"->Map("Reg"->macRxFilterReg, "Addr"->Bits("h05"))
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

  val ocpSelRam = Mux(ocpMasterReg.Addr(constRamAddrWidth-1) <= EthernetConstants.constEthFrameLength.U, true.B, false.B)
  val validRamPop = Mux(ethRxCtrlRegMap("fifoPop")("Reg") === true.B && !fifoFullReg, true.B, false.B)

  val ethRamWrEn = Mux(ethByteDvReg && stateReg === stCollectPayload, true.B, false.B)
  val ocpRamRdEn = Mux(ocpMasterReg.Cmd === OcpCmd.RD && ocpSelRam, true.B, false.B)

  switch(stateReg) {
    is(sWaitSFD) {
      when(miiRx.io.ethSof && !fifoFullReg){
        stateReg := stCollectPayload
      }
    }
    is(stCollectPayload) {
      when(miiRx.io.ethEof){
        stateReg := sWaitSFD
        wrRamIdReg := wrRamIdReg + 1.U
        fifoCountReg := fifoCountReg + 1.U
      }
    }
  }

  when(ethRamWrEn) {
    byteCntReg := byteCntReg + 1.U
  }

  when(validRamPop){
    rdRamIdReg := rdRamIdReg + 1.U
    fifoCountReg := fifoCountReg - 1.U
  }

  /**
    * Regs Master Control
    */
  ethRxCtrlRegMap("fifoPop")("Reg") := false.B
  respReg := OcpResp.NULL
  when(ocpSelRam){
    dataReg := io.ramPorts(rdRamIdReg).S.Data
    respReg := Mux(!fifoFullReg, io.ramPorts(rdRamIdReg).S.Resp, OcpResp.FAIL)
  }.otherwise{
    when(ocpMasterReg.Cmd === OcpCmd.RD){
      respReg := OcpResp.DVA
      when(ocpMasterReg.Addr(4,2) === ethRxCtrlRegMap("rxEn")("Addr")){
        dataReg := ethRxCtrlRegMap("rxEn")("Reg")
      }.elsewhen(ocpMasterReg.Addr(4,2) === ethRxCtrlRegMap("fifoCount")("Addr")){
        dataReg := ethRxCtrlRegMap("fifoCount")("Reg")
      }.elsewhen(ocpMasterReg.Addr(4,2) === ethRxCtrlRegMap("fifoEmpty")("Addr")){
        dataReg := ethRxCtrlRegMap("fifoEmpty")("Reg")
      }.elsewhen(ocpMasterReg.Addr(4,2) === ethRxCtrlRegMap("fifoFull")("Addr")){
        dataReg := ethRxCtrlRegMap("fifoFull")("Reg")
      }.elsewhen(ocpMasterReg.Addr(4,2) === ethRxCtrlRegMap("fifoPop")("Addr")){
        dataReg := ethRxCtrlRegMap("fifoPop")("Reg")
      }.elsewhen(ocpMasterReg.Addr(4,2) === ethRxCtrlRegMap("macRxFilter")("Addr")){
        dataReg := ethRxCtrlRegMap("macRxFilter")("Reg")
      }
    }.elsewhen(ocpMasterReg.Cmd === OcpCmd.WR){
      when(ocpMasterReg.Addr(4,2) === ethRxCtrlRegMap("rxEn")("Addr")){
        ethRxCtrlRegMap("rxEn")("Reg") := orR(ocpMasterReg.Data)
        respReg := OcpResp.DVA
      }.elsewhen(ocpMasterReg.Addr(4,2) === ethRxCtrlRegMap("fifoCount")("Addr")){
        respReg := OcpResp.ERR
      }.elsewhen(ocpMasterReg.Addr(4,2) === ethRxCtrlRegMap("fifoEmpty")("Addr")){
        respReg := OcpResp.ERR
      }.elsewhen(ocpMasterReg.Addr(4,2) === ethRxCtrlRegMap("fifoFull")("Addr")){
        respReg := OcpResp.ERR
      }.elsewhen(ocpMasterReg.Addr(4,2) === ethRxCtrlRegMap("fifoPop")("Addr")){
        ethRxCtrlRegMap("fifoPop")("Reg") := true.B //as long as you write something it is set to one which is automatically reset in the next clock cycle
        respReg := OcpResp.DVA
      }.elsewhen(ocpMasterReg.Addr(4,2) === ethRxCtrlRegMap("macRxFilter")("Addr")){
        ethRxCtrlRegMap("macRxFilter")("Reg") := ocpMasterReg.Data
        respReg := OcpResp.DVA
      }
    }.otherwise{
      respReg := OcpResp.NULL
    }
  }

  /**
    * Ram Master Control
    */
  for(ramId <- 0 until numFrameBuffers){
    ramMasterRegs(ramId).Addr := RegNext(Mux(ramWrSelOH(ramId) && ethRamWrEn, byteCntReg(constRamAddrWidth-1, 2), ocpMasterReg.Addr(constRamAddrWidth-1, 2)))
    ramMasterRegs(ramId).ByteEn := RegNext(Mux(ramWrSelOH(ramId) && ethRamWrEn, UIntToOH(byteCntReg(1, 0), width = 4), Mux(ocpRamRdEn, ocpMasterReg.ByteEn, Bits("b0000"))))
    ramMasterRegs(ramId).Cmd := RegNext(Mux(ramWrSelOH(ramId) && ethRamWrEn, OcpCmd.WR, Mux(ocpRamRdEn, OcpCmd.RD, OcpCmd.IDLE)))
    ramMasterRegs(ramId).Data := RegNext(ethByteReg)
  }

  /**
    * I/O plumbing
    */
  io.ocp.S.Data := dataReg
  io.ocp.S.Resp := respReg
  miiRx.io.miiChannel <> io.miiChannel
  miiRx.io.rxEn := ethRxCtrlRegMap("rxEn")("Reg")
  for(ramId <- 0 until numFrameBuffers){
    io.ramPorts(ramId).M := ramMasterRegs(ramId)
  }
}

object EthRxController extends App{
  chiselMain(Array[String]("--backend", "v", "--targetDir", "generated/"+this.getClass.getSimpleName.dropRight(1)),
    () => Module(new EthRxController(2, 64)))
}
