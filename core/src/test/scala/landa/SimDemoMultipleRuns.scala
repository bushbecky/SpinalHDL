package landa

import spinal.core.SimManagedApi._
import spinal.core._
import spinal.sim._

import scala.util.Random


object SimDemoMultipleRuns {
  class Dut extends Component {
    val io = new Bundle {
      val a, b, c = in UInt (8 bits)
      val result = out UInt (8 bits)
    }
    io.result := RegNext(io.a + io.b - io.c) init(0)
  }

  def main(args: Array[String]): Unit = {
    val compiled = SimConfig(rtl = new Dut).withWave.compile
    compiled.doManagedSim("longTest"){ dut =>
      fork{
        dut.clockDomain.assertReset()
        dut.clockDomain.fallingEdge()
        sleep(10)
        while(true){
          dut.clockDomain.clockToggle()
          sleep(5)
        }
      }

      repeatSim(times = 100) {
        val a, b, c = Random.nextInt(256)
        dut.io.a #= a
        dut.io.b #= b
        dut.io.c #= c
        dut.clockDomain.waitActiveEdge()
        assert(dut.io.result.toInt == ((a+b-c) & 0xFF))
      }
    }

    compiled.doManagedSim("shortTest"){ dut =>
      fork{
        dut.clockDomain.assertReset()
        dut.clockDomain.fallingEdge()
        sleep(10)
        while(true){
          dut.clockDomain.clockToggle()
          sleep(5)
        }
      }

      repeatSim(times = 50) {
        val a, b, c = Random.nextInt(256)
        dut.io.a #= a
        dut.io.b #= b
        dut.io.c #= c
        dut.clockDomain.waitActiveEdge()
        assert(dut.io.result.toInt == ((a+b-c) & 0xFF))
      }
    }
  }
}