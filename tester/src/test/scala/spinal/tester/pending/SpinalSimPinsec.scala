package spinal.tester.pending

import spinal.core.ClockDomain
import spinal.core.sim._
import spinal.lib.soc.pinsec.{Pinsec, PinsecConfig}

object SpinalSimPinsec {
  def main(args: Array[String]): Unit = {
    SimConfig(new Pinsec(PinsecConfig.default))
      .allOptimisation
      .doSim{dut =>
        ClockDomain(dut.io.axiClk, dut.io.asyncReset).forkStimulus(10)
        ClockDomain(dut.io.vgaClk).forkStimulus(40)
        ()
      }
  }
}
