// See README.md for license details.

package ethcontroller.design

import Chisel.{Module, Tester, chiselMainTest}
import ethcontroller.protocols.{EthernetFrame, EthernetTesting}

import scala.sys.process._

class MIITxTester(dut: MIITx, frame: EthernetFrame) extends Tester(dut) {
  // Shifting of discrete numbers for clarity
  poke(dut.io.startTx, true)
  poke(dut.io.miiChannel.dv, 1)
  poke(dut.io.miiChannel.clk, 0)
  step(1)
  poke(dut.io.startTx, false)
  println("(Testing preamble)")
  if(peek(dut.io.busy) == 0){
    poke(dut.io.macDataDv, true)
    poke(dut.io.macData, BigInt(frame.preamble))
    step(1)
    poke(dut.io.macDataDv, false)
  }
  for(i <- 0 until frame.preambleNibbles.length+2) {
    poke(dut.io.miiChannel.clk, 0)
    step(2)
    poke(dut.io.miiChannel.clk, 1)
    step(2)
  }
}

object MIITxTester extends App {
  private val pathToVCD = "generated/" + this.getClass.getSimpleName.dropRight(1)
  private val nameOfVCD = this.getClass.getSimpleName.dropRight(7) + ".vcd"

  try {
    chiselMainTest(Array("--genHarness", "--test", "--backend", "c",
      "--compile", "--vcd", "--targetDir", "generated/" + this.getClass.getSimpleName.dropRight(1)),
      () => Module(new MIITx())) {
      dut => new MIITxTester(dut, EthernetTesting.mockupPTPEthFrameOverIpUDP)
    }
  } finally {
    "gtkwave " + pathToVCD + "/" + nameOfVCD + " " + pathToVCD + "/" + "view.sav &" !
  }
}
