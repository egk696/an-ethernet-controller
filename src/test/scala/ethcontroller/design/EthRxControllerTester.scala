package ethcontroller.design

import Chisel.{Module, Tester, chiselMainTest}
import ethcontroller.protocols.{EthernetFrame, EthernetTesting}

import scala.sys.process._

class EthRxControllerTester(dut: EthRxController, frame: EthernetFrame) extends Tester(dut) {

}

object EthRxControllerTester extends App{
  chiselMainTest(Array("--genHarness", "--test", "--backend", "c",
    "--compile", "--vcd", "--targetDir", "generated/" + this.getClass.getSimpleName.dropRight(1)),
    () => Module(new EthRxController(8))) {
    dut => new EthRxControllerTester(dut, EthernetTesting.mockupPTPEthFrameOverIpUDP)
  }
  "gtkwave " + "generated/" + this.getClass.getSimpleName.dropRight(1) + "/" + this.getClass.getSimpleName.dropRight(7) + ".vcd" !
}

