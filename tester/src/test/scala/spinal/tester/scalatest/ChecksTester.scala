package spinal.tester.scalatest

import org.scalatest.FunSuite
import spinal.core._
import spinal.core.internals.GraphUtils
import spinal.lib.com.i2c._
import spinal.lib.com.uart.{UartCtrl, UartCtrlGenerics}
import spinal.lib.{Delay, StreamFifo}

import scala.sys.process._

object CheckTester{
  def checkFailure(func : => Unit) : Boolean = {
    try{func} catch {
      case e: Throwable => {
        print(e)
        return true
      }
    }
    return false
  }

  def generationShouldFaild(gen : => Component): Unit ={
    assert(checkFailure{SpinalVhdl(gen)})
    assert(checkFailure{SpinalVerilog(gen)})
  }

  def generationShouldPass(gen : => Component): Unit ={
    assert(!checkFailure{SpinalVhdl(gen)})
    assert(!checkFailure{SpinalVerilog(gen)})
  }
}

class ChecksTester extends FunSuite  {
  import CheckTester._


  test("BlackBoxInputUnconnected"){
    generationShouldFaild(new Component{
      class Sub extends BlackBox{
        val input = in Bool()
      }
      val sub = new Sub
    })
  }

  test("literalWidth"){
    val t = SpinalVhdl(new Component{
      val a = B"32'h0"
    }).toplevel

    assert(widthOf(t.a) == 32)
  }


  test("componentNamedByIo") {
    val t = SpinalVerilog(new Component{
      val miaou = new Component{
        val io = new Bundle {
          val x = out Bool()
        }
        assert(io.x.getName() == "io_x")
      }.io
    }.setDefinitionName("TopLevel")).toplevel

    assert(t.miaou.component.getName() == "miaou")
    assert(t.miaou.getName() == "io")
  }



  test("checkWidthAssignment") {
    generationShouldFaild(new Component{
      val output = out Bits(8 bits)
      output := "00"
    })
  }


  test("checkCombinatorialLoop") {
    generationShouldFaild(new Component{
      val input = in Bits(8 bits)
      val cond = in Bool

      val tempA = Bits(4 bits)
      val output = out Bits(8 bits)

      tempA := input(7 downto 4)
      val tempB = Bits(4 bits)
      tempB := tempA

      when(cond){
        tempA(1) := tempB(0)
      }

      output(3 downto 0) := input (3 downto 0)
      output(7 downto 4) := tempB
    })
  }

  test("checkNoPartialAssignment") {
    generationShouldPass(new Component{
      val cond = in Bool
      val input = in Bits(8 bits)
      val output = out Bits(8 bits)
      when(cond){
        output(7 downto 1) := input.resized
        when(cond){
          output(0) := cond
        } otherwise{
          output(0) := cond
        }
      }otherwise{
        output := input
      }
    })

    generationShouldFaild(new Component{
      val cond = in Bool
      val input = in Bits(8 bits)
      val output = out Bits(8 bits)
      when(cond){
        output(7 downto 1) := input.resized
        when(cond){

        } otherwise{
          output(0) := cond
        }
      }otherwise{
        output := input
      }
    })
  }

  test("checkNoMissingDefault") {
    generationShouldPass(new Component{
      val cond = in Bool
      val input = in Bits(8 bits)
      val output = out Bits(8 bits)
      when(cond){
        output := input
      }otherwise{
        when(cond) {
          output := input
          when(cond){
            output := input
          }
        } otherwise {
          output := input
        }
      }
    })

    generationShouldFaild(new Component{
      val cond = in Bool
      val input = in Bits(8 bits)
      val output = out Bits(8 bits)
      when(cond){
        output := input
      }otherwise{
        when(cond) {
          when(cond){
            output := input
          }
        } otherwise {
          output := input
        }
      }
    })
  }

  test("checkClockCrossing") {
    generationShouldFaild(new Component{
      val clockA = in Bool
      val clockB = in Bool

      val areaA = new ClockingArea(ClockDomain(clockA)){
        val reg = Reg(Bool)
        reg := in(Bool)
      }

      val areaB = new ClockingArea(ClockDomain(clockB)){
        val reg = Reg(Bool)
        reg := areaA.reg
        val output = out Bool()
        output := reg
      }
    })
  }

  test("checkClockCrossingCheckingCheckSourcesPaths") {
    generationShouldPass(new Component{
      val clock = in Bool
      val clockA =  Bool
      val clockB =  Bool

      clockA := clock
      val sub = new Component{
        val cIn = in Bool()
        val cOut = out Bool()

        val tmp = Bool()
        tmp := cIn
        cOut := tmp
      }

      sub.cIn := clock
      clockB := sub.cOut
      val areaA = new ClockingArea(ClockDomain(clockA)){
        val reg = Reg(Bool)
        reg := in(Bool)
      }

      val areaB = new ClockingArea(ClockDomain(clockB)){
        val reg = Reg(Bool)
        reg := areaA.reg
        val output = out Bool()
        output := reg
      }
    })
  }

  test("checkClockCrossingCheckingCheckSourcesPathsFalure") {
    generationShouldFaild(new Component{
      val clock1 = in Bool
      val clock2 = in Bool
      val clockA =  Bool
      val clockB =  Bool

      clockA := clock1
      val sub = new Component{
        val cIn = in Bool()
        val cOut = out Bool()

        val tmp = Bool()
        tmp := cIn
        cOut := tmp
      }

      sub.cIn := clock2
      clockB := sub.cOut
      val areaA = new ClockingArea(ClockDomain(clockA)){
        val reg = Reg(Bool)
        reg := in(Bool)
      }

      val areaB = new ClockingArea(ClockDomain(clockB)){
        val reg = Reg(Bool)
        reg := areaA.reg
        val output = out Bool()
        output := reg
      }
    })
  }

  test("checkNoInputAssignement") {
    generationShouldFaild(new Component{
      val input = in Bool()
      val output = out Bool()
      output := input
      input := False
    })
  }

  test("checkNoSubOutputAssignement") {
    generationShouldFaild(new Component{
      val sub = new Component{
        val output = out(True)
      }
      sub.output := False
    })
  }



  test("checkNoSubSignalAssignement") {
    generationShouldFaild(new Component{
      val sub = new Component{
        val tmp = True
      }
      sub.tmp := False
    })
  }

  test("checkNoOverrides") {
    generationShouldPass(new Component{
      val a = Bool
      a := True
      when(True === True) {
        a := False
      }
    })

    generationShouldPass(new Component{
      val a = Bool
      when(True === True) {
        a := False
      } otherwise {
        a := True
      }
    })

    generationShouldFaild(new Component{
      val a = Bool
      a := True
      a := False
    })
    generationShouldFaild(new Component{
      val a = Bool
      a := True
      when(True === True) {
        a := False
        a := True
      }
    })

    generationShouldFaild(new Component{
      val a = Bool
      when(True === True) {
        a := False
      }
      a := True
    })

    generationShouldFaild(new Component{
      val sub = new Component{
        val a = in Bool()
        val result = out Bool()
        result := a
      }

      val result = out Bool()
      result := sub.result
    })

    generationShouldFaild(new Component{
      val sub = new Component{
        val result = out Bool()
      }

      val result = out Bool()
      result := sub.result
    })

    generationShouldFaild(new Component{
      val sub = new Component{
        val a = in Bool()
        val result = out Bool()
        result := a
      }

      val result = out Bool()
      result := sub.result
      when(True){
        sub.a := True
      }
    })



  }

  test("checkNoResetFail") {
    generationShouldFaild(new Component{
      ClockDomain(in Bool) {
        val output = out(RegInit(False)).setName("aaa")
      }
    })
  }

  test("checkOnlyIoInIoBundle") {
    class CheckOnlyIoInBundle extends Component{
      val io = new Bundle{
        val x = Bool()
      }
    }
    generationShouldFaild(new CheckOnlyIoInBundle)
  }
}



class NameingTester extends FunSuite {
  import CheckTester._


  test("reflectionNamming") {
    val t = SpinalVhdl(new Component{
      val a = new Area{
        val aa = Bool
        val bb = new Area{
          val aaa = Bool
          val bbb = Vec(Bool,4)
          val ccc = Vec(new Bundle{
            val aaaa = Bool
            val bbbb = Vec(Bool,8)
            val cccc = Vec( Vec( Vec(Bool,8),8),8)
            val dddd = List.fill(4)(Bool)
            val eeee = List.fill(4)(List.fill(4)(Bool))
          },4)
        }
      }
      val b = Bool
    }).toplevel

    assert(t.b.getName() == "b")
    assert(t.a.aa.getName() == "a_aa")
    assert(t.a.bb.aaa.getName() == "a_bb_aaa")
    assert(t.a.bb.bbb(2).getName() == "a_bb_bbb_2")
    assert(t.a.bb.ccc(3).aaaa.getName() == "a_bb_ccc_3_aaaa")
    assert(t.a.bb.ccc(3).bbbb(6).getName() == "a_bb_ccc_3_bbbb_6")
    assert(t.a.bb.ccc(3).cccc(6)(5)(4).getName() == "a_bb_ccc_3_cccc_6_5_4")
    assert(t.a.bb.ccc(3).dddd(3).getName() == "a_bb_ccc_3_dddd_3")
    assert(t.a.bb.ccc(3).eeee(3)(2).getName() == "a_bb_ccc_3_eeee_3_2")
  }

  test("Apb3I2cCtrl"){
    def configI2C = I2cSlaveMemoryMappedGenerics(
      ctrlGenerics       = I2cSlaveGenerics(),
      addressFilterCount = 0,
      masterGenerics     = I2cMasterMemoryMappedGenerics(timerWidth = 32)
    )
    val dut = SpinalConfig(defaultClockDomainFrequency = FixedFrequency(50 MHz)).generateVerilog(new Apb3I2cCtrl(configI2C)).toplevel
    assert(GraphUtils.countNames(dut) == 154)
  }

  test("Uart"){
    val dut = SpinalVerilog(new UartCtrl(UartCtrlGenerics())).toplevel
    assert(GraphUtils.countNames(dut) == 81)
  }
}