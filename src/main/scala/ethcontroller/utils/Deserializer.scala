// See README.md for license details.

package ethcontroller.utils

import Chisel._

/**
  * Deserialize an M-bit input to an N-bit output based on a specified order
  *
  * @param inputWidth  the bit width of the serial input
  * @param outputWidth the bit width of the parallel
  * @param msbFirst    the serial input order
  */
class Deserializer(msbFirst: Boolean = false, inputWidth: Int = 4, outputWidth: Int = 8) extends Module {
  val io = new Bundle() {
    val en = Bool(INPUT)
    val clr = Bool(INPUT)
    val shiftIn = Bits(INPUT, width = inputWidth)
    val shiftOut = Bits(OUTPUT, width = outputWidth)
    val done = Bool(OUTPUT)
  }

  val shiftReg = RegInit(0.U(outputWidth.W))

  // Shift-register
  when(io.clr) {
    shiftReg := 0.U
  }.elsewhen(io.en) {
    if (msbFirst) {
      shiftReg := shiftReg(inputWidth - 1, 0) ## io.shiftIn
      shiftReg:= shiftReg(outputWidth - 1, inputWidth) ## shiftReg(outputWidth - inputWidth - 1, 0)
    } else {
      shiftReg := shiftReg(outputWidth - inputWidth - 1, 0) ## shiftReg(outputWidth - 1, inputWidth)
      shiftReg:= shiftReg(outputWidth - 1, outputWidth - inputWidth) ## io.shiftIn
    }
  }

  val countReg = Reg(init = UInt(outputWidth / inputWidth - 1, width = log2Floor(outputWidth / inputWidth) + 1))
  val doneReg = Reg(init = Bool(false))

  // Shift Counter
  when(io.clr) {
    countReg := (outputWidth / inputWidth - 1).U
    doneReg := false.B
  }.elsewhen(io.en) {
    when(countReg === 0.U) {
      countReg := (outputWidth / inputWidth - 1).U
      doneReg := true.B
    }.otherwise {
      countReg := countReg - 1.U
      doneReg := false.B
    }
  }.elsewhen(doneReg) {
    doneReg := false.B
  }

  io.shiftOut := shiftReg
  io.done := doneReg
}


object Deserializer extends App {
  chisel3.Driver.execute(Array("--target-dir", "generated"), () => new Deserializer(false, 4, 8))
}
