// See README.md for license details.

package ethcontroller.design

import Chisel.{Module, Tester, chiselMainTest}
import ethcontroller.protocols.{EthernetFrame, EthernetTesting}

import scala.language.postfixOps
import scala.sys.process._

class MIIRxTester(dut: MIIRx, frame: EthernetFrame) extends Tester(dut) {
  // Shifting of discrete numbers for clarity
  poke(dut.io.miiChannel.col, 0)
  poke(dut.io.miiChannel.crs, 0)
  poke(dut.io.rxEn, true)
  poke(dut.io.miiChannel.dv, 1)
  poke(dut.io.miiChannel.clk, 0)
  step(1)
  println("(Testing preamble)")
  for (nibble <- frame.preambleNibbles) {
    poke(dut.io.miiChannel.data, nibble)
    poke(dut.io.miiChannel.clk, 0)
    step(3)
    peek(dut.deserializePHYByte.countReg)
    peek(dut.deserializePHYByte.doneReg)
    peek(dut.deserializePHYByte.shiftReg)
    peek(dut.regBuffer)
    poke(dut.io.miiChannel.clk, 1)
    step(3)
  }
  poke(dut.io.miiChannel.clk, 0)
  step(3)
  poke(dut.io.miiChannel.clk, 1)
  expect(dut.io.ethSof, true, "--checking for start-of-frame detected")
  expect(dut.regBuffer, BigInt(frame.preamble), "--checking for preamble registered")
  step(3)
  println("Testing EoF")
  poke(dut.io.miiChannel.clk, 0)
  poke(dut.io.miiChannel.dv, 0)
  step(3)
  expect(dut.io.ethEof, true, "--checking for end-of-frame detected")
  poke(dut.io.miiChannel.clk, 1)
  step(3)
  poke(dut.io.miiChannel.clk, 0)
  step(3)
}

object MIIRxTester extends App {
  private val pathToVCD = "generated/" + this.getClass.getSimpleName.dropRight(1)
  private val nameOfVCD = this.getClass.getSimpleName.dropRight(7) + ".vcd"

  try {
    chiselMainTest(Array("--genHarness", "--test", "--backend", "c",
      "--compile", "--vcd", "--targetDir", "generated/" + this.getClass.getSimpleName.dropRight(1)),
      () => Module(new MIIRx())) {
      dut => new MIIRxTester(dut, EthernetTesting.mockupPTPEthFrameOverIpUDP)
    }
  } finally {
    "gtkwave " + pathToVCD + "/" + nameOfVCD + " " + pathToVCD + "/" + "view.sav &" !
  }
}
