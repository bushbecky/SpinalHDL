package spinal.tester.scalatest

import spinal.core._
import spinal.core.formal._
import spinal.lib.{StreamFifo, History, OHToUInt}
import spinal.lib.formal._

class FormalFifoTester extends SpinalFormalFunSuite {
  test("fifo-verify all") {
    val initialCycles = 2
    val inOutDelay = 2
    val coverCycles = 10
    FormalConfig
      .withBMC(10)
      .withProve(10)
      .withCover(coverCycles)
      // .withDebug
      .doVerify(new Component {
        val depth = 4
        val dut = FormalDut(new StreamFifo(UInt(7 bits), depth))
        val reset = ClockDomain.current.isResetActive

        assumeInitial(reset)

        val inValue = anyseq(UInt(7 bits))
        val inValid = anyseq(Bool())
        val outReady = anyseq(Bool())
        dut.io.push.payload := inValue
        dut.io.push.valid := inValid
        dut.io.pop.ready := outReady

        // assume no valid while reset and one clock later.
        when(reset || past(reset)) {
          assume(inValid === False)
        }

        dut.io.push.formalAssumesSlave()
        dut.io.pop.formalAssertsMaster()

        dut.io.push.formalCovers()
        // back to back transaction cover test.
        dut.io.pop.formalCovers(coverCycles - initialCycles - inOutDelay - 1)

        val d1 = anyconst(UInt(7 bits))
        val d2 = anyconst(UInt(7 bits))

        val (d1_in, d2_in) = dut.io.push.formalAssumesOrder(d1, d2)
        val (d1_out, d2_out) = dut.io.pop.formalAssertsOrder(d1, d2)

        when(!d1_in) { assume(!dut.formalContains(d1)) }
        when(d1_in && !d1_out) { assert(dut.formalCount(d1) === 1) }

        when(!d2_in) { assume(!dut.formalContains(d2)) }
        when(d2_in && !d2_out) { assert(dut.formalCount(d2) === 1) }

        when(d1_in && d2_in && !d1_out) { assert(!d2_out) }

        def getCompId(x: UInt): UInt = {
          val id = OHToUInt(dut.formalCheck(_ === x.pull()).asBits)
          val extId = id +^ depth
          val compId = CombInit(extId)
          when(id >= dut.logic.popPtr) {
            compId := id.resized
          }
          compId
        }
        when(d1_in && d2_in && !d1_out && !d2_out) {
          assert(getCompId(d1) < getCompId(d2))
        }
      })
  }
}
