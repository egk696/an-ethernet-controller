package ethcontroller.protocols

import Chisel._

object EthernetConstants {

  // Constants
  val constEthFrameLength = 1518 * 8 //bits
  val constEthMacLength = 6 * 8 //bits
  val constFCSLength = 4 * 8 //bits
  val constSFD = Bits("h55555555555555D5", width = 64)
  val constIFG = Bits("h0000000000000000", width = 64)
  val constVLANt1 = Bits("h8100", width = 16)
  val constVLANt2 = Bits("h88A8", width = 16)
  val constVLANt3 = Bits("h9100", width = 16)
  val constMPLSt1 = Bits("h8847", width = 16)
  val constMPLSt2 = Bits("h8848", width = 16)
  val constIPv4t = Bits("h0800", width = 16)
  val constIPv6t = Bits("h86DD", width = 16)
  val constPTP2t = Bits("h88F7", width = 16)
  val constPTP4t1 = Bits("h013F", width = 16)
  val constPTP4t2 = Bits("h0140", width = 16)
  val constPTPGeneralPort = 319.U
  val constPTPEventPort = 320.U
  val constPTPSyncType = Bits("h00")
  val constPTPFollowType = Bits("h08")
  val constPTPDlyReqType = Bits("h01")
  val constPTPDlyRplyType = Bits("h09")

}
