// See README.md for license details.

package ethcontroller.design

import Chisel.{Module, Tester, chiselMainTest}
import ethcontroller.protocols.{EthernetFrame, EthernetTesting}

import scala.language.postfixOps
import scala.sys.process._

class MIITxTester(dut: MIITx, frame: EthernetFrame) extends Tester(dut) {
  // Shifting of discrete numbers for clarity
  var byteAddr = 0
  //  poke(dut.io.startTx, true)
  poke(dut.io.miiChannel.clk, 0)
  step(3)
  //  poke(dut.io.startTx, false)
  println("-- Testing the preamble")
  while(byteAddr < frame.preamble.length){
    if(peek(dut.io.ready) == 1){
      poke(dut.io.macDataDv, true)
      poke(dut.io.macData, BigInt(frame.preamble(byteAddr)))
      byteAddr+=1
    }
    peek(dut.serializeByteToNibble.selHi)
    peek(dut.serializeByteToNibble.selLo)
    poke(dut.io.miiChannel.clk, 0)
    step(1)
    poke(dut.io.macDataDv, false)
    step(2)
    poke(dut.io.miiChannel.clk, 1)
    step(3)
  }
  while(peek(dut.io.ready) == 0){
    poke(dut.io.miiChannel.clk, 0)
    step(3)
    poke(dut.io.miiChannel.clk, 1)
    step(3)
  }
  //  poke(dut.io.endTx, true)
  poke(dut.io.miiChannel.clk, 0)
  step(1)
  //  poke(dut.io.endTx, true)
  step(2)
  poke(dut.io.miiChannel.clk, 1)
  step(3)
  poke(dut.io.miiChannel.clk, 0)
  step(3)
  poke(dut.io.miiChannel.clk, 1)
  step(3)
  expect(dut.io.miiChannel.dv, false, "Expecting an idle bus")
  expect(dut.serializeByteToNibble.io.done, true, "Expecting the serializer to have finished")
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
