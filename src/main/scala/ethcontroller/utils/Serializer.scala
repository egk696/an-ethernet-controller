// See README.md for license details.

package ethcontroller.utils

import Chisel._

/**
  * Serializes an M-bit input to an N-bit output based on a specified order
  * after an initial load
  *
  * @param inputWidth  the bit width of the parallel input
  * @param outputWidth the bit width of the serial output
  * @param msbFirst    the serial output order
  */
class Serializer(msbFirst: Boolean = false, inputWidth: Int = 8, outputWidth: Int = 4) extends Module {
  val io = new Bundle() {
    val load = Bool(INPUT)
    val en = Bool(INPUT)
    val shiftIn = Bits(INPUT, width = inputWidth)
    val shiftOut = Bits(OUTPUT, width = outputWidth)
    val dv = Bool(OUTPUT)
    val done = Bool(OUTPUT)
  }

  val constShiftStages = inputWidth / outputWidth
  val shiftReg = Reg(init = Bits(0, width = inputWidth))
  val busyReg = Reg(init = Bool(false))
  val doneReg = Reg(init = Bool(false))
  val countReg = Reg(init = UInt(constShiftStages - 1, width = log2Floor(constShiftStages) + 1))
  val selHi = UInt()
  val selLo = UInt()

  // Shift-register
  when(~busyReg && io.load) {
    shiftReg := io.shiftIn
    busyReg := true.B
    doneReg := false.B
  }.elsewhen(io.en && busyReg) {
    when(countReg === 0.U) {
      countReg := (constShiftStages - 1).U
      doneReg := true.B
    }.otherwise {
      countReg := countReg - 1.U
      doneReg := false.B
    }
  }.elsewhen(doneReg) {
    busyReg := false.B
  }

  if (msbFirst) {
    selHi := countReg * outputWidth.U + outputWidth.U - 1.U
    selLo := countReg * outputWidth.U
  } else {
    selHi := (constShiftStages.U - 1.U - countReg) * outputWidth.U + outputWidth.U - 1.U
    selLo := (constShiftStages.U - 1.U - countReg) * outputWidth.U
  }

  /**
    * I/O plumbing
    */
  io.shiftOut := shiftReg(selHi, selLo)
  io.done := doneReg
  io.dv := busyReg
}

object Serializer extends App {
  chiselMain(Array[String]("--backend", "v", "--targetDir", "generated/" + this.getClass.getSimpleName.dropRight(1)),
    () => Module(new Serializer(false, 8, 4)))
}
