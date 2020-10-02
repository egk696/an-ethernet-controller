// See README.md for license details.

package ethcontroller.protocols

/**
  * Mockup Ethernet frame
  * Nibbles in the frame are in LSB-first per byte order
  */
abstract class EthernetFrame {

  val PREAMBLE_END = 0xD5
  val IP_UDP_PROTOCOL = 0x11
  val PTP_FOLLOW_UP_TYPE = 0x8

  //Vars
  val preamble: Array[Byte]
  val dstMac: Array[Byte]
  val srcMac: Array[Byte]
  val ethType: Array[Byte]
  val data: Array[Byte]

  //Special cases
  val ipHeader: Array[Byte]
  val udpHeader: Array[Byte]
  val ptpHeader: Array[Byte]
  val ptpBody: Array[Byte]
  val ptpSuffix: Array[Byte]
  val fcs: Array[Byte]
  val igp: Array[Byte]

  //Getters for nibbles
  def preambleNibbles: Array[Int] = EthernetUtils.dataBytesToNibbles(preamble, msbFirst = false)

  def dstMacNibbles: Array[Int] = EthernetUtils.dataBytesToNibbles(dstMac, msbFirst = false)

  def srcMacNibbles: Array[Int] = EthernetUtils.dataBytesToNibbles(srcMac, msbFirst = false)

  def ethTypeNibbles: Array[Int] = EthernetUtils.dataBytesToNibbles(ethType, msbFirst = false)

  def dataNibbles: Array[Int] = EthernetUtils.dataBytesToNibbles(data, msbFirst = false)

  def ipHeaderNibbles: Array[Int] = EthernetUtils.dataBytesToNibbles(ipHeader, msbFirst = false)

  def udpHeaderNibbles: Array[Int] = EthernetUtils.dataBytesToNibbles(udpHeader, msbFirst = false)

  def ptpHeaderNibbles: Array[Int] = EthernetUtils.dataBytesToNibbles(ptpHeader, msbFirst = false)

  def ptpBodyNibbles: Array[Int] = EthernetUtils.dataBytesToNibbles(ptpBody, msbFirst = false)

  def ptpSuffixNibbles: Array[Int] = EthernetUtils.dataBytesToNibbles(ptpSuffix, msbFirst = false)

  def fcsNibbles: Array[Int] = EthernetUtils.dataBytesToNibbles(fcs, msbFirst = false)

  def igpNibbles: Array[Int] = EthernetUtils.dataBytesToNibbles(igp, msbFirst = false)
}
