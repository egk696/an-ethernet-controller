package ethcontroller.interfaces

import Chisel.{Bits, Bool, Bundle, INPUT}

class MIIChannel extends Bundle{
  /** Clock from the PHY */
  val clk = Bool(INPUT)
  /** Received data valid */
  val dv = Bool()
  /** Received nibble data */
  val data = Bits(width=4)
  /** Signal could not be decoded to data */
  val err = Bool()
  /** Carrier-sense signal */
  val crs = Bool()
  /** Collision detection signal */
  val col =  Bool()
}
