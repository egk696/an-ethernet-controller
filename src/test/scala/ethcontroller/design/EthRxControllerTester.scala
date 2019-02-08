package ethcontroller.design

import Chisel.{Module, Tester, chiselMainTest}
import ethcontroller.protocols.{EthernetFrame, EthernetTesting, EthernetUtils}

import scala.sys.process._

class EthRxControllerTester(dut: EthRxController, frame: EthernetFrame, injectNoFrames: Int) extends Tester(dut) {

  def initPHY2MIIData(clockDiv: Int): Unit ={
    poke(dut.io.miiChannel.col, 0)
    poke(dut.io.miiChannel.crs, 0)
    poke(dut.io.miiChannel.clk, 0)
    step(clockDiv)
  }

  def txPHY2MIIData(nibble: Int, clockDiv: Int): Unit ={
    poke(dut.io.miiChannel.dv, 1)
    poke(dut.io.miiChannel.data, nibble)
    poke(dut.io.miiChannel.clk, 0)
    step(clockDiv)
    poke(dut.io.miiChannel.clk, 1)
    step(clockDiv)
  }

  def stopTxPHY2MII(clockDiv: Int): Unit ={
    poke(dut.io.miiChannel.dv, 0)
    poke(dut.io.miiChannel.clk, 0)
    step(clockDiv)
    poke(dut.io.miiChannel.clk, 1)
    step(clockDiv)
  }

  def peekEthRxStatus(): Unit ={
    peek(dut.stateReg)
    peek(dut.ethByteReg)
    peek(dut.ethByteDvReg)
    peek(dut.wrRamIdReg)
    peek(dut.fifoCountReg)
    peek(dut.fifoEmptyReg)
    peek(dut.fifoFullReg)
  }

  def testSingleFrameReception(frame: EthernetFrame, initTime: Long): Unit ={
    initPHY2MIIData(4)
    println("Testing preamble")
    for (nibble <- frame.preambleNibbles) {
      txPHY2MIIData(nibble, 4)
      peek(dut.miiRx.deserializePHYByte.doneReg)
      peek(dut.miiRx.deserializePHYByte.shiftReg)
      peek(dut.miiRx.regBuffer)
    }
    println("Testing MAC")
    for (nibble <- frame.dstMacNibbles ++ frame.srcMacNibbles ++ frame.ethTypeNibbles) {
      txPHY2MIIData(nibble, 4)
      peekEthRxStatus()
    }
    println("Testing Raw Ethernet frame II payload")
    for (payloadData <- 0 until 45) {
      for (nibble <- EthernetUtils.byteToNibble(payloadData, true)) {
        txPHY2MIIData(nibble, 4)
        peekEthRxStatus()
      }
    }
    println("Testing EoF")
    stopTxPHY2MII(4)
    peekEthRxStatus()
  }

  /**
    * Testing EthernetRxController
    */
  println("Test Starting...")
  var time : Long = 0x53C38A1000000000L
  for(i <- 0 until injectNoFrames){
    println("...")
    println("TEST FRAME ITERATION #" + i + " at t = " + time.toHexString)
    testSingleFrameReception(frame, time)
    println("END TEST FRAME ITERATION #"+ i + " at t = " + time.toHexString)
    step(1)
    step(1)
    println("...")
    println("...")
  }

}

object EthRxControllerTester extends App{
  private val pathToVCD = "generated/" + this.getClass.getSimpleName.dropRight(1)
  private val nameOfVCD = this.getClass.getSimpleName.dropRight(7) + ".vcd"

  chiselMainTest(Array("--genHarness", "--test", "--backend", "c",
    "--compile", "--vcd", "--targetDir", pathToVCD),
    () => Module(new EthRxController(2, 64))) {
    dut => new EthRxControllerTester(dut, EthernetTesting.mockupPTPEthFrameOverIpUDP, 3)
  }

  "gtkwave " + pathToVCD + "/" + nameOfVCD + " " + pathToVCD + "/" + "view.sav" !
}

