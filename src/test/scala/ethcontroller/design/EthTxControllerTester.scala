// See README.md for license details.

package ethcontroller.design

import Chisel.{Module, Tester, chiselMainTest}
import ethcontroller.protocols.{EthernetFrame, EthernetTesting}
import ocp.OcpCmd

import scala.language.postfixOps
import scala.sys.process._


class EthTxControllerTester(dut: EthTxController, frame: EthernetFrame, injectNoFrames: Int) extends Tester(dut) {

  val MII_TICKS_TO_CLKS: Int = 2

  var miiClkValue: Int = 0
  var numberOfSysSteps: Int = 0

  def testSingleFrameTransmission(frame: EthernetFrame, initTime: Long): Unit = {
    initPHY2MII(4)
    println("Testing OCP write to buffer")
    ocpLoadFrameToBuffer()
    println("Testing EthTx transmission")
    println("Testing preamble")
    //    for (nibble <- frame.preambleNibbles) {
    //      stepWithMIIClock(6)
    //      peek(dut.miiTx.serializeByteToNibble.doneReg)
    //      peek(dut.miiTx.serializeByteToNibble.shiftReg)
    //      peekEthTxStatus()
    //    }
    //    println("Testing MAC")
    //    for (nibble <- frame.dstMacNibbles ++ frame.srcMacNibbles ++ frame.ethTypeNibbles) {
    //      stepWithMIIClock(6)
    //      peekEthTxStatus()
    //    }
    //    println("Testing Raw Ethernet frame II payload")
    //    for (payloadData <- 0 until 45) {
    //      for (nibble <- frame.dataNibbles) {
    //        stepWithMIIClock(6)
    //        peekEthTxStatus()
    //      }
    //    }
    println("Testing EoF")
    stepWithMIIClock()
  }

  def initPHY2MII(steps: Int): Unit = {
    poke(dut.io.miiChannel.col, 0)
    poke(dut.io.miiChannel.crs, 0)
    poke(dut.io.miiChannel.clk, 0)
    step(steps)
  }

  def ocpLoadFrameToBuffer(): Unit = {
    val totalFrame = frame.dstMac ++ frame.srcMac ++ frame.ethType ++ frame.data
    var ocpAddr = 0x0
    var byteCnt = 0x1
    for (byte <- totalFrame) {
      poke(dut.io.ocp.M.Addr, ocpAddr)
      poke(dut.io.ocp.M.ByteEn, byteCnt)
      poke(dut.io.ocp.M.Data, byte)
      poke(dut.io.ocp.M.Cmd, OcpCmd.WR.litValue())
      stepWithMIIClock()
      if (byteCnt == 8) {
        byteCnt = 1
        ocpAddr += 0x1
      } else {
        byteCnt = byteCnt << 1
      }
    }

    poke(dut.io.ocp.M.Cmd, OcpCmd.IDLE.litValue())
    stepWithMIIClock()
    poke(dut.io.ocp.M.Addr, 1 << dut.constRamAddrWidth | 0x18)
    poke(dut.io.ocp.M.ByteEn, 0xF)
    poke(dut.io.ocp.M.Data, 0x1)
    poke(dut.io.ocp.M.Cmd, OcpCmd.WR.litValue())
    stepWithMIIClock()
    poke(dut.io.ocp.M.Cmd, OcpCmd.IDLE.litValue())
    stepWithMIIClock()
    expect(dut.fifoPushReg, true)
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

  def stepWithMIIClock(): Unit = {
    poke(dut.io.miiChannel.clk, miiClkValue)
    step(1)
    if (numberOfSysSteps > MII_TICKS_TO_CLKS) {
      miiClkValue = 1 - miiClkValue
      numberOfSysSteps = 0
    } else {
      numberOfSysSteps += 1
    }
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
    stepWithMIIClock()
    stepWithMIIClock()
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
    dut => new EthTxControllerTester(dut, EthernetTesting.mockupTTEPCFFrame, 5)
  }

  var waveHandle = "gtkwave " + pathToVCD + "/" + nameOfVCD + " " + pathToVCD + "/" + "view.sav" !

  println("gtkwave running as PID:" + waveHandle)
}



