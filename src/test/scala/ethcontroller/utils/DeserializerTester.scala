// See README.md for license details.

package ethcontroller.utils

import Chisel._
import ethcontroller.protocols.{EthernetFrame, EthernetTesting}

import scala.language.postfixOps
import scala.sys.process._

class DeserializerTester(dut: Deserializer, frame: EthernetFrame) extends Tester(dut) {
  // Shifting of discrete numbers for clarity
  poke(dut.io.en, true)
  for (i <- frame.preambleNibbles) {
    poke(dut.io.shiftIn, i)
    peek(dut.io.shiftOut)
    peek(dut.io.done)
    step(1)
  }
  expect(dut.io.done, true)
  expect(dut.io.shiftOut, frame.PREAMBLE_END)
  poke(dut.io.en, false)
  step(1)
  poke(dut.io.clr, true)
  step(1)
  expect(dut.countReg, 8 / 4 - 1)
  expect(dut.shiftReg, 0)
  expect(dut.doneReg, false)
}

object DeserializerTester extends App {
  private val pathToVCD = "generated/" + this.getClass.getSimpleName.dropRight(1)
  private val nameOfVCD = this.getClass.getSimpleName.dropRight(7) + ".vcd"

  try {
    chiselMainTest(Array("--genHarness", "--test", "--backend", "c",
      "--compile", "--vcd", "--targetDir", "generated/" + this.getClass.getSimpleName.dropRight(1)),
      () => Module(new Deserializer(false, 4, 8))) {
      dut => new DeserializerTester(dut, EthernetTesting.mockupPTPEthFrameOverIpUDP)
    }
  } finally {
    "gtkwave " + pathToVCD + "/" + nameOfVCD + " " + pathToVCD + "/" + "view.sav" !
  }
}
