package ethcontroller.protocols

import Chisel._

object EthernetConstants {

  // Constants
  val constEthFrameLength = 1518 * 8 //bits
  val constEthMacLength = 6 * 8 //bits
  val constSFD = Bits("h55555555555555D5")
  val constIFG = Bits("h0000000000000000")
  val constVLANt1 = Bits("h8100")
  val constVLANt2 = Bits("h88A8")
  val constVLANt3 = Bits("h9100")
  val constMPLSt1 = Bits("h8847")
  val constMPLSt2 = Bits("h8848")
  val constIPv4t = Bits("h0800")
  val constIPv6t = Bits("h86DD")
  val constPTP2t = Bits("h88F7")
  val constPTP4t1 = Bits("h013F")
  val constPTP4t2 = Bits("h0140")
  val constPTPGeneralPort = 319.U
  val constPTPEventPort = 320.U
  val constPTPSyncType = Bits("h00")
  val constPTPFollowType = Bits("h08")
  val constPTPDlyReqType = Bits("h01")
  val constPTPDlyRplyType = Bits("h09")

}
