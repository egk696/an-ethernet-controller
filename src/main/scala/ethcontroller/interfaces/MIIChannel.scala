// See README.md for license details.

package ethcontroller.interfaces

import Chisel.{Bits, Bool, Bundle, INPUT, OUTPUT}

class MIIChannel extends Bundle {
  /** Clock from the PHY */
  val clk = Bool(INPUT)
  /** Received data valid */
  val dv = Bool(OUTPUT)
  /** Received nibble data */
  val data = Bits(OUTPUT, width = 4)
  /** Signal could not be decoded to data */
  val err = Bool(INPUT)
  /** Carrier-sense signal */
  val crs = Bool(INPUT)
  /** Collision detection signal */
  val col = Bool(INPUT)
}
