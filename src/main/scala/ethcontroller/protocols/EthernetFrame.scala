package ethcontroller.protocols

/**
  * Mockup Ethernet frame
  * Nibbles in the frame are in LSB-first per byte order
  */
abstract class EthernetFrame{

  val PREAMBLE_END = 0xD5
  val IP_UDP_PROTOCOL = 0x11
  val PTP_FOLLOW_UP_TYPE = 0x8

  //Vars
  val preamble : Array[Byte]
  val dstMac : Array[Byte]
  val srcMac : Array[Byte]
  val ethType : Array[Byte]
  val ipHeader : Array[Byte]
  val udpHeader : Array[Byte]
  val ptpHeader : Array[Byte]
  val ptpBody : Array[Byte]
  val ptpSuffix : Array[Byte]
  val fcs : Array[Byte]
  val igp : Array[Byte]

  //Getters for nibbles
  def preambleNibbles : Array[Int] = dataBytesToNibbles(preamble, msbFirst = false)
  def dstMacNibbles : Array[Int] = dataBytesToNibbles(dstMac, msbFirst = false)
  def srcMacNibbles : Array[Int] = dataBytesToNibbles(srcMac, msbFirst = false)
  def ethTypeNibbles : Array[Int] = dataBytesToNibbles(ethType, msbFirst = false)
  def ipHeaderNibbles : Array[Int] = dataBytesToNibbles(ipHeader, msbFirst = false)
  def udpHeaderNibbles : Array[Int] = dataBytesToNibbles(udpHeader, msbFirst = false)
  def ptpHeaderNibbles : Array[Int] = dataBytesToNibbles(ptpHeader, msbFirst = false)
  def ptpBodyNibbles : Array[Int] = dataBytesToNibbles(ptpBody, msbFirst = false)
  def ptpSuffixNibbles : Array[Int] = dataBytesToNibbles(ptpSuffix, msbFirst = false)
  def fcsNibbles : Array[Int] = dataBytesToNibbles(fcs, msbFirst = false)
  def igpNibbles : Array[Int] = dataBytesToNibbles(igp, msbFirst = false)

  /**
    * Converts a byte to an array of nibbles
    * @param byte gets broken down to nibbles
    * @param msbFirst controls the order of the nibbles
    * @return an array of two nibbles
    */
  def byteToNibble(byte: Int, msbFirst: Boolean): Array[Int] ={
    val nibbles = new Array[Int](2)
    nibbles(0) = byte & 0x0F
    nibbles(1) = (byte & 0xF0) >> 4
    if(msbFirst){
      nibbles.reverse
    } else {
      nibbles
    }
  }

  /**
    * Converts an array of bytes to an array of nibbles
    * @param bytes the data to be broken down to nibbles
    * @param msbFirst controls the order of the nibbles per byte
    * @return an array of nibbles size bytes.size*2
    */
  def dataBytesToNibbles(bytes: Array[Byte], msbFirst: Boolean): Array[Int] = {
    val nibbles = new Array[Int](bytes.length*2)
    var i = 0
    for (byte <- bytes){
      val tempByteNibbles = byteToNibble(byte, msbFirst)
      nibbles(i) = tempByteNibbles(0)
      nibbles(i+1) = tempByteNibbles(1)
      i=i+2
    }
    nibbles
  }

  /**
    * Lazy way for converting Integer values in array to bytes
    * @param xs Integer Array values
    * @return Array of bytes
    */
  def toBytes(xs: Int*) = xs.map(_.toByte).toArray

}
