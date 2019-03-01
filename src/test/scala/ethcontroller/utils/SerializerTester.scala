// See README.md for license details.

package ethcontroller.utils

import Chisel._
import ethcontroller.protocols.{EthernetFrame, EthernetTesting}

import scala.sys.process._

class SerializerTester(dut: Serializer, frame: EthernetFrame) extends Tester(dut) {
  // Shifting of discrete numbers for clarity
  poke(dut.io.en, true)
  for (i <- frame.preamble) {
    step(1)
    poke(dut.io.load, true)
    poke(dut.io.shiftIn, i.toInt)
    step(1)
    poke(dut.io.load, false)
    do {
      step(1)
    } while (peek(dut.io.done) == 0)
    peek(dut.io.shiftOut)
  }
  expect(dut.io.done, true)
  expect(dut.io.shiftOut, frame.PREAMBLE_END)
  step(1)
  poke(dut.io.en, false)
  expect(dut.countReg, 8 / 4 - 1)
  expect(dut.shiftReg, 0)
  expect(dut.doneReg, false)
}

object SerializerTester extends App {
  private val pathToVCD = "generated/" + this.getClass.getSimpleName.dropRight(1)
  private val nameOfVCD = this.getClass.getSimpleName.dropRight(7) + ".vcd"

  try {
    chiselMainTest(Array("--genHarness", "--test", "--backend", "c",
      "--compile", "--vcd", "--targetDir", "generated/" + this.getClass.getSimpleName.dropRight(1)),
      () => Module(new Serializer(false, 8, 4))) {
      dut => new SerializerTester(dut, EthernetTesting.mockupPTPEthFrameOverIpUDP)
    }
  } finally {
    "gtkwave " + pathToVCD + "/" + nameOfVCD + " " + pathToVCD + "/" + "view.sav" !
  }
}