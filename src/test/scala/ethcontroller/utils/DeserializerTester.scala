// See README.md for license details.

package ethcontroller.utils

import Chisel._
import ethcontroller.protocols.{EthernetFrame, EthernetTesting}

class DeserializerTester(dut: Deserializer, frame: EthernetFrame) extends Tester(dut) {
  // Shifting of discrete numbers for clarity
  poke(dut.io.en, true)
  for(i <- frame.preambleNibbles) {
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
  expect(dut.countReg, 8/4-1)
  expect(dut.shiftReg, 0)
  expect(dut.doneReg, false)
}

object DeserializerTester extends App{
    chiselMainTest(Array("--genHarness", "--test", "--backend", "c",
      "--compile", "--targetDir", "generated/" + this.getClass.getSimpleName.dropRight(1)),
      () => Module(new Deserializer(false, 4, 8))) {
      dut => new DeserializerTester(dut, EthernetTesting.mockupPTPEthFrameOverIpUDP)
    }
}
