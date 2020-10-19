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
  val shiftRegs = Reg(Vec(constShiftStages, UInt(outputWidth.W))) // meaning Vec(constShiftStages,Reg(init = 0.U(outputWidth.W))) but chisel3 sucks
  val busyReg = Reg(init = Bool(false))
  val doneReg = Reg(init = Bool(false))
  val countReg = Reg(init = (constShiftStages - 1).U ((log2Floor(constShiftStages) + 1).W))
  val selHi = Wire(0.U)
  val selLo = Wire(0.U)

  // Shift-register
  when(!busyReg && io.load) {

    for(i <- 0 to constShiftStages-1)
    {
      shiftRegs(i) := io.shiftIn((i*outputWidth),0)
    }

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

//  if (msbFirst) {
//    io.shiftOut := shiftRegs((constShiftStages-countReg))
//  } else {
//    io.shiftOut := shiftRegs(countReg)
//  }


  io.shiftOut := shiftRegs(countReg)
  io.done := doneReg
  io.dv := busyReg
}

object Serializer extends App {
  chisel3.Driver.execute(Array("--target-dir", "generated"), () => new Serializer(false, 16, 4))
}

