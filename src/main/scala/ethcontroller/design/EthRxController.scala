package ethcontroller.design

import Chisel._
import ethcontroller.interfaces.MIIChannel
import ethcontroller.protocols.EthernetConstants
import ocp._

class EthRxController(numFrameBuffers: Int) extends Module {
  val io = new Bundle(){
    val ocp = new OcpCoreSlavePort(32, 32)
    val ramPorts = Vec.fill(numFrameBuffers){new OcpCoreMasterPort(32, 32)}
    val miiChannel = new MIIChannel().asInput()
  }

  val constRamIdWidth = log2Ceil(numFrameBuffers)
  val constRamAddrWidth = log2Ceil(EthernetConstants.constEthFrameLength)
  val miiRx = Module(new MIIRx())

  /**
    * Registers and devices insta
    */
  val ethByteReg = Reg(next = miiRx.io.ethByte)
  val ethByteDvReg = Reg(next = miiRx.io.ethByteDv)
  val ocpMasterReg = Reg(next = io.ocp.M)
  val wrRamIdReg = Reg(init = UInt(0, width = constRamIdWidth))
  val rdRamIdReg = Reg(init = UInt(0, width = constRamIdWidth))
  val byteCntReg = Reg(init = UInt(0, width = constRamAddrWidth))
  val ramMasterRegs = Vec.fill(numFrameBuffers){Reg(new OcpCoreMasterSignals(constRamAddrWidth, 32))}
  val dataReg = Reg(init = Bits(0, width = 32))
  val respReg = Reg(init = OcpResp.NULL)
  val ethRxCtrlRegMap = Map(
    "rxEnReg"->Map("Val"->Reg(init = Bool(false)), "Addr"->Bits("h00")),
    "fifoCount"->Map("Val"->Reg(init = UInt(0, width = constRamIdWidth)), "Addr"->Bits("h01")),
    "fifoEmpty"->Map("Val"->Reg(init = Bool(false)), "Addr"->Bits("h02")),
    "fifoFull"->Map("Val"->Reg(init = Bool(false)), "Addr"->Bits("h03")),
    "macRxFilter"->Map("Val"->Reg(init = UInt(0, width = EthernetConstants.constEthMacLength)), "Addr"->Bits("h04"))
  )

  /**
    * Fifo Management
    */
  val fifoCount = (wrRamIdReg - rdRamIdReg + numFrameBuffers.U) % numFrameBuffers.U
  val fifoEmpty = ethRxCtrlRegMap("fifoCount")("Val") === 0.U && rdRamIdReg + 1.U === wrRamIdReg
  val fifoFull = ethRxCtrlRegMap("fifoCount")("Val") === 0.U && wrRamIdReg + 1.U === rdRamIdReg

  /**
    * State machine Control
    */
  val sWaitSFD :: stCollectPayload :: Nil = Enum(UInt(), 2)
  val stateReg = Reg(init = sWaitSFD)

  val ramWrSelOH = UIntToOH(wrRamIdReg)
  val ramRdSelOH = UIntToOH(rdRamIdReg)

  val ocpSelRam = !ocpMasterReg.Addr(constRamAddrWidth-1)

  val validRamOcpRd = rdRamIdReg =/= wrRamIdReg

  val ethRamWrEn = ethByteDvReg && stateReg === stCollectPayload
  val ocpRamRdEn = ocpMasterReg.Cmd === OcpCmd.RD && ocpSelRam && validRamOcpRd

  when(ethRamWrEn) {
    byteCntReg := byteCntReg + 1.U
  }

  when(ocpRamRdEn && ~fifoEmpty){
    rdRamIdReg := rdRamIdReg + 1.U
  }

  switch(stateReg) {
    is(sWaitSFD) {
      when(miiRx.io.ethSof & !fifoFull){
        stateReg := stCollectPayload
      }
    }
    is(stCollectPayload) {
      when(miiRx.io.ethEof){
        stateReg := sWaitSFD
        wrRamIdReg := wrRamIdReg + 1.U
      }
    }
  }

  /**
    * Regs Master Control
    */
  ethRxCtrlRegMap("fifoCount")("Val") := fifoCount
  ethRxCtrlRegMap("fifoEmpty")("Val") := fifoEmpty
  ethRxCtrlRegMap("fifoFull")("Val") := fifoFull
  respReg := OcpResp.NULL
  when(ocpSelRam){
    dataReg := io.ramPorts(rdRamIdReg).S.Data
    respReg := Mux(validRamOcpRd, io.ramPorts(rdRamIdReg).S.Resp, OcpResp.ERR)
  }.otherwise{
    when(ocpMasterReg.Cmd === OcpCmd.RD){
      respReg := OcpResp.DVA
      when(ocpMasterReg.Addr(4,2) === ethRxCtrlRegMap("rxEnReg")("Addr")){
        dataReg := ethRxCtrlRegMap("rxEnReg")("Val")
      }.elsewhen(ocpMasterReg.Addr(4,2) === ethRxCtrlRegMap("fifoCount")("Addr")){
        dataReg := ethRxCtrlRegMap("fifoCount")("Val")
      }.elsewhen(ocpMasterReg.Addr(4,2) === ethRxCtrlRegMap("fifoEmpty")("Addr")){
        dataReg := ethRxCtrlRegMap("fifoEmpty")("Val")
      }.elsewhen(ocpMasterReg.Addr(4,2) === ethRxCtrlRegMap("fifoFull")("Addr")){
        dataReg := ethRxCtrlRegMap("fifoFull")("Val")
      }.elsewhen(ocpMasterReg.Addr(4,2) === ethRxCtrlRegMap("macRxFilter")("Addr")){
        dataReg := ethRxCtrlRegMap("macRxFilter")("Val")
      }
    }.elsewhen(ocpMasterReg.Cmd === OcpCmd.WR){
      when(ocpMasterReg.Addr(4,2) === ethRxCtrlRegMap("rxEnReg")("Addr")){
        ethRxCtrlRegMap("rxEnReg")("Val") := orR(ocpMasterReg.Data)
        respReg := OcpResp.DVA
      }.elsewhen(ocpMasterReg.Addr(4,2) === ethRxCtrlRegMap("fifoCount")("Addr")){
        respReg := OcpResp.ERR
      }.elsewhen(ocpMasterReg.Addr(4,2) === ethRxCtrlRegMap("fifoEmpty")("Addr")){
        respReg := OcpResp.ERR
      }.elsewhen(ocpMasterReg.Addr(4,2) === ethRxCtrlRegMap("fifoFull")("Addr")){
        respReg := OcpResp.ERR
      }.elsewhen(ocpMasterReg.Addr(4,2) === ethRxCtrlRegMap("macRxFilter")("Addr")){
        ethRxCtrlRegMap("macRxFilter")("Val") := ocpMasterReg.Data
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
    io.ramPorts(ramId).M.Addr := RegNext(Mux(ramWrSelOH(ramId) && ethRamWrEn, byteCntReg(constRamAddrWidth-1, 2), ocpMasterReg.Addr(constRamAddrWidth-1, 2)))
    io.ramPorts(ramId).M.ByteEn := RegNext(Mux(ramWrSelOH(ramId) && ethRamWrEn, UIntToOH(byteCntReg(1, 0), width = 4), Mux(ocpRamRdEn, ocpMasterReg.ByteEn, Bits("b0000"))))
    io.ramPorts(ramId).M.Cmd := RegNext(Mux(ramWrSelOH(ramId) && ethRamWrEn, OcpCmd.WR, Mux(ocpRamRdEn, OcpCmd.RD, OcpCmd.IDLE)))
    io.ramPorts(ramId).M.Data := ethByteReg
  }

  /**
    * I/O plumbing
    */
  io.ocp.S.Data := dataReg
  io.ocp.S.Resp := respReg
  miiRx.io.miiChannel <> io.miiChannel
  miiRx.io.rxEn := ethRxCtrlRegMap("rxEnReg")("Val")
}

object EthRxController extends App{
  chiselMain(Array[String]("--backend", "v", "--targetDir", "generated/"+this.getClass.getSimpleName.dropRight(1)),
    () => Module(new EthRxController(2)))
}
