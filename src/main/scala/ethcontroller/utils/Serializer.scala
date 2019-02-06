package ethcontroller.utils

import Chisel._

/**
  * Serializes an M-bit input to an N-bit output based on a specified order
  * @param inputWidth the bit width of the parallel input
  * @param outputWidth the bit width of the serial output
  * @param msbFirst the serial output order
  */
class Serializer(msbFirst: Boolean = false, inputWidth: Int = 8, outputWidth: Int = 4) extends Module {
  val io = new Bundle() {
    val en = Bool(INPUT)
    val clr = Bool(INPUT)
    val shiftIn = Bits(INPUT, width = inputWidth)
    val shiftOut = Bits(OUTPUT, width = outputWidth)
    val done = Bool(OUTPUT)
  }

  val shiftReg = RegNext(io.shiftIn)

  // Shift-register
  when(io.clr) {
    shiftReg := 0.U
  } .elsewhen(io.en) {
    if (msbFirst) {
      io.shiftOut := shiftReg(outputWidth-1, inputWidth)
      shiftReg(outputWidth-1, inputWidth) := shiftReg(outputWidth-inputWidth-1,0)
    } else {
      io.shiftOut := shiftReg(outputWidth-inputWidth-1, 0)
      shiftReg(outputWidth-inputWidth-1, 0) := shiftReg(outputWidth-1, inputWidth)
    }
  }

  val countReg = Reg(init = UInt(outputWidth/inputWidth-1, width=log2Floor(outputWidth/inputWidth)+1))
  val doneReg = Reg(init = Bool(false))

  // Shift Counter
  when(io.clr) {
    countReg := (outputWidth / inputWidth - 1).U
    doneReg := false.B
  } .elsewhen(io.en) {
    when(countReg === 0.U) {
      countReg := (outputWidth / inputWidth - 1).U
      doneReg := true.B
    }   .otherwise {
      countReg := countReg - 1.U
      doneReg := false.B
    }
  } .elsewhen(doneReg) {
    doneReg := false.B
  }

  io.shiftOut := shiftReg
  io.done := doneReg


}
