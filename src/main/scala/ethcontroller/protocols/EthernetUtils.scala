// See README.md for license details.

package ethcontroller.protocols

object EthernetUtils {
  /**
    * Converts a byte to an array of nibbles
    *
    * @param byte     gets broken down to nibbles
    * @param msbFirst controls the order of the nibbles
    * @return an array of two nibbles
    */
  def byteToNibble(byte: Int, msbFirst: Boolean): Array[Int] = {
    val nibbles = new Array[Int](2)
    nibbles(0) = byte & 0x0F
    nibbles(1) = (byte & 0xF0) >> 4
    if (msbFirst) {
      nibbles.reverse
    } else {
      nibbles
    }
  }

  /**
    * Converts an array of bytes to an array of nibbles
    *
    * @param bytes    the data to be broken down to nibbles
    * @param msbFirst controls the order of the nibbles per byte
    * @return an array of nibbles size bytes.size*2
    */
  def dataBytesToNibbles(bytes: Array[Byte], msbFirst: Boolean): Array[Int] = {
    val nibbles = new Array[Int](bytes.length * 2)
    var i = 0
    for (byte <- bytes) {
      val tempByteNibbles = byteToNibble(byte, msbFirst)
      nibbles(i) = tempByteNibbles(0)
      nibbles(i + 1) = tempByteNibbles(1)
      i = i + 2
    }
    nibbles
  }

  /**
    * Lazy way for converting Integer values in array to bytes
    *
    * @param xs Integer Array values
    * @return Array of bytes
    */
  def toBytes(xs: Int*) = xs.map(_.toByte).toArray
}
