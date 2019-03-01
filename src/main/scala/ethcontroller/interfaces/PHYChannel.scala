// See README.md for license details.

package ethcontroller.interfaces

import Chisel.{Bool, Bundle, INPUT, OUTPUT}

class PHYChannel extends Bundle {
  /** Management data clock to the PHY */
  val mdc = Bool(OUTPUT)
  /** Management output data */
  val mdo = Bool(OUTPUT)
  /** Management input data */
  val mdi = Bool(INPUT)
  /** Management data tri-state */
  val md_t = Bool(OUTPUT)
}
