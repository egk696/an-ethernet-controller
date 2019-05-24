// See README.md for license details.

package ethcontroller.design

import Chisel.{Module, Tester, chiselMainTest}
import ethcontroller.protocols.{EthernetFrame, EthernetTesting}
import ocp.OcpCmd

import scala.language.postfixOps


class EthTxControllerTester(dut: EthTxController, frame: EthernetFrame, injectNoFrames: Int) extends Tester(dut) {

  def initPHY2MII(clockDiv: Int): Unit = {
    poke(dut.io.miiChannel.col, 0)
    poke(dut.io.miiChannel.crs, 0)
    poke(dut.io.miiChannel.clk, 0)
    step(clockDiv)
  }

  def ocpLoadFrameToBuffer(clockDiv: Int): Unit = {
    val totalFrame = frame.dstMac ++ frame.srcMac ++ frame.ethType
    var ocpAddr = 0x0
    var byteCnt = 0x1
    for (byte <- frame.dstMac ++ frame.srcMac ++ frame.ethType) {
      poke(dut.io.ocp.M.Addr, ocpAddr)
      poke(dut.io.ocp.M.ByteEn, byteCnt)
      poke(dut.io.ocp.M.Data, byte)
      poke(dut.io.ocp.M.Cmd, OcpCmd.WR.litValue())
      step(clockDiv)
      if (byteCnt == 8) {
        byteCnt = 1
        ocpAddr += 0x1
      } else {
        byteCnt = byteCnt << 1
      }
    }
    poke(dut.io.ocp.M.Cmd, OcpCmd.IDLE.litValue())
    step(clockDiv)
    poke(dut.io.ocp.M.Addr, 0x4018)
    poke(dut.io.ocp.M.ByteEn, 0xF)
    poke(dut.io.ocp.M.Data, 0x1)
    poke(dut.io.ocp.M.Cmd, OcpCmd.WR.litValue())
    step(clockDiv)
    poke(dut.io.ocp.M.Cmd, OcpCmd.IDLE.litValue())
    step(clockDiv)
  }

  def stepMIIClock(clockDiv: Int): Unit = {
    poke(dut.io.miiChannel.clk, 0)
    step(clockDiv)
    poke(dut.io.miiChannel.clk, 1)
    step(clockDiv)
  }

  def peekEthTxStatus(): Unit = {
    peek(dut.stateReg)
    peek(dut.byteCntReg)
    peek(dut.rdRamIdReg)
    peek(dut.wrRamIdReg)
    peek(dut.fifoCountReg)
    peek(dut.fifoEmptyReg)
    peek(dut.fifoFullReg)
    peek(dut.txFrameSizeReg)
    peek(dut.miiTx.transmittingReg)
  }

  def testSingleFrameTransmission(frame: EthernetFrame, initTime: Long): Unit = {
    initPHY2MII(4)
    println("Testing OCP write to buffer")
    ocpLoadFrameToBuffer(1)
    //    println("Testing preamble")
    //    for (nibble <- frame.preambleNibbles) {
    //      stepMIIClock(6)
    //      peek(dut.miiTx.serializeByteToNibble.doneReg)
    //      peek(dut.miiTx.serializeByteToNibble.shiftReg)
    //      peekEthTxStatus()
    //    }
    //    println("Testing MAC")
    //    for (nibble <- frame.dstMacNibbles ++ frame.srcMacNibbles ++ frame.ethTypeNibbles) {
    //      stepMIIClock(6)
    //      peekEthTxStatus()
    //    }
    //    println("Testing Raw Ethernet frame II payload")
    //    for (payloadData <- 0 until 45) {
    //      for (nibble <- EthernetUtils.byteToNibble(payloadData, true)) {
    //        stepMIIClock(6)
    //        peekEthTxStatus()
    //      }
    //    }
    //    println("Testing EoF")
    stepMIIClock(6)
    peekEthTxStatus()
  }

  /**
    * Testing EthernetRxController
    */
  println("Test Starting...")
  var time: Long = 0x53C38A1000000000L
  for (i <- 0 until injectNoFrames) {
    println("...")
    println("TEST FRAME ITERATION #" + i + " at t = " + time.toHexString)
    testSingleFrameTransmission(frame, time)
    println("END TEST FRAME ITERATION #" + i + " at t = " + time.toHexString)
    step(1)
    step(1)
    println("...")
    println("...")
  }

}

object EthTxControllerTester extends App {
  private val pathToVCD = "generated/" + this.getClass.getSimpleName.dropRight(1)
  private val nameOfVCD = this.getClass.getSimpleName.dropRight(7) + ".vcd"

  chiselMainTest(Array("--genHarness", "--test", "--backend", "c",
    "--compile", "--vcd", "--targetDir", pathToVCD),
    () => Module(new EthTxController(4, 64))) {
    dut => new EthTxControllerTester(dut, EthernetTesting.mockupPTPEthFrameOverIpUDP, 5)
  }

  //  "gtkwave " + pathToVCD + "/" + nameOfVCD + " " + pathToVCD + "/" + "view.sav" !
}



