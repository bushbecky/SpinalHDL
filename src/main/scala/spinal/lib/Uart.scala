package spinal.lib

import spinal._


class Uart extends Bundle with Interface {
  val txd = Bool()
  val rxd = Bool()

  override def asMaster: this.type = {
    out(txd)
    in(rxd)
    this
  }
  override def asSlave: this.type = asMaster.flip
}


object UartStopType extends SpinalEnum {
  val eStop1bit, eStop2bit = Value

  val toBitCount = SpinalMap(
    eStop1bit() -> UInt(0),
    eStop2bit() -> UInt(1)
  )
}

object UartParityType extends SpinalEnum {
  val eParityNone, eParityEven, eParityOdd = Value
}


import spinal.lib.UartParityType._
import spinal.lib.UartStopType._

class UartConfig(dataWidthMax: Int = 8) extends Bundle {
  val dataLength = UInt(log2Up(dataWidthMax) bit)
  val stop = UartStopType()
  val parity = UartParityType()
}

class UartCtrlIo(dataWidthMax: Int = 8, clockDividerWidth: Int = 20) extends Bundle {
  val config = in(new UartConfig(dataWidthMax))
  val clockDivider = in UInt (clockDividerWidth bit)
  val write = slave Handshake (Bits(dataWidthMax bit))
  val read = master Flow (Bits(dataWidthMax bit))
  val uart = master(new Uart)
}

class UartCtrl(dataWidthMax: Int = 8, clockDividerWidth: Int = 20, preSamplingSize: Int = 1, samplingSize: Int = 5, postSamplingSize: Int = 2) extends Component {
  val io = new UartCtrlIo(dataWidthMax)
  val txToRxClockDividerFactor = preSamplingSize + samplingSize + postSamplingSize
  assert(isPow2(txToRxClockDividerFactor))

  val tx = new UartCtrlTx(dataWidthMax, clockDividerWidth + log2Up(txToRxClockDividerFactor))
  val rx = new UartCtrlRx(dataWidthMax, clockDividerWidth, preSamplingSize, samplingSize, postSamplingSize)

  tx.io.config := io.config
  rx.io.config := io.config

  tx.io.clockDivider := (io.clockDivider ## BitsSet(log2Up(txToRxClockDividerFactor) bit)).toUInt
  rx.io.clockDivider := io.clockDivider

  tx.io.write << io.write
  rx.io.read >> io.read

  io.uart.txd := tx.io.txd
  rx.io.rxd := io.uart.rxd
}

object UartCtrlTxState extends SpinalEnum {
  val sIdle, sStart, sData, sParity, sStop = Value
}

class UartCtrlTx(dataWidthMax: Int = 8, clockDividerWidth: Int = 24) extends Component {
  val io = new Bundle {
    val config = in(new UartConfig(dataWidthMax))
    val clockDivider = in UInt (clockDividerWidth bit)
    val write = slave Handshake (Bits(dataWidthMax bit))
    val txd = out Bool()
  }

  val timer = new Area {
    val counter = Reg(io.clockDivider);
    val reset = Bool()
    val tick = counter === UInt(0)

    counter := counter - UInt(1)
    when(tick || reset) {
      counter := io.clockDivider
    }
  }

  val tickCounter = new Area {
    val value = Reg(UInt(Math.max(dataWidthMax, 2) bit));
    val reset = Bool()

    when(timer.tick) {
      value := value + UInt(1)
    }
    when(reset) {
      value := UInt(0)
    }
  }

  val stateMachine = new Area {
    import UartCtrlTxState._


    val state = RegInit(sIdle())
    val paritySum = Reg(Bool());
    val dataBuffer = Reg(io.write.data)

    val lookingForJob = Bool()
    val txd = Bool()

    when(timer.tick) {
      paritySum := paritySum ^ txd
    }

    lookingForJob := Bool(false)
    tickCounter.reset := Bool(false)
    timer.reset := Bool(false)
    txd := Bool(true)
    switch(state) {
      is(sIdle) {
        lookingForJob := Bool(true)
      }
      is(sStart) {
        txd := Bool(false)
        when(timer.tick) {
          state := sData
          paritySum := io.config.parity === eParityOdd
          tickCounter.reset := Bool(true)
        }
      }
      is(sData) {
        txd := dataBuffer(tickCounter.value)
        when(timer.tick) {
          when(tickCounter.value === io.config.dataLength) {
            tickCounter.reset := Bool(true)
            when(io.config.parity === eParityNone) {
              state := sStop
            } otherwise {
              state := sParity
            }
          }
        }
      }
      is(sParity) {
        txd := paritySum
        when(timer.tick) {
          state := sStop
          tickCounter.reset := Bool(true)
        }
      }
      is(sStop) {
        when(timer.tick) {
          when(tickCounter.value === toBitCount(io.config.stop)) {
            state := sIdle
            lookingForJob := Bool(true)
          }
        }
      }
    }

    io.write.ready := Bool(false)
    when(lookingForJob && io.write.valid) {
      io.write.ready := Bool(true)
      timer.reset := Bool(true)
      dataBuffer := io.write.data
      state := sStart
    }
  }

  io.txd := RegNext(stateMachine.txd, Bool(true))

}

object UartCtrlRxState extends SpinalEnum {
  val sIdle, sStart, sData, sParity, sStop = Value
}

class UartCtrlRx(dataWidthMax: Int = 8, clockDividerWidth: Int = 21, preSamplingSize: Int = 1, samplingSize: Int = 5, postSamplingSize: Int = 2) extends Component {
  val io = new Bundle {
    val config = in(new UartConfig(dataWidthMax))
    val clockDivider = in UInt (clockDividerWidth bit)
    val read = master Flow (Bits(dataWidthMax bit))
    val rxd = in Bool()
  }

  val clockDivider = new Area {
    val counter = RegInit(UInt(0, clockDividerWidth bit))
    val tick = counter === UInt(0)

    counter := counter - UInt(1)
    when(tick) {
      counter := io.clockDivider
    }
  }

  val sampler = new Area {
    val frontBuffer = CCBuffer(io.rxd)
    val samples = RegInit(BitsSet(samplingSize bit))
    when(clockDivider.tick) {
      samples := samples ## frontBuffer
    }
    val value = RegNext(MajorityVote(samples))
    val event = RegNext(clockDivider.tick)
  }

  val baud = new Area {
    val counter = Reg(UInt(log2Up(preSamplingSize + samplingSize + postSamplingSize) bit))
    val tick = Bool()

    tick := Bool(false)
    when(sampler.event) {
      counter := counter - UInt(1)
      when(counter === UInt(0)) {
        tick := Bool(true)
        counter := UInt(preSamplingSize + samplingSize + postSamplingSize - 1)
      }
    }

    //TODO verify UInt(sampleCount / 2 - 1)
    def reset: Unit = counter := UInt(preSamplingSize + samplingSize / 2 - 1)
    def value = sampler.value
  }


  val baudCounter = new Area {
    val value = Reg(UInt(Math.max(dataWidthMax, 2) bit))
    def reset: Unit = value := UInt(0)

    when(baud.tick) {
      value := value + UInt(1)
    }
  }

  val stateMachine = new Area {
    import UartCtrlRxState._

    
    val state = RegInit(sIdle())
    val paritySum = Reg(Bool())
    val dataBuffer = Reg(io.read.data)

    when(baud.tick) {
      paritySum := paritySum ^ baud.value
    }

    io.read.valid := Bool(false)
    switch(state) {
      is(sIdle) {
        when(sampler.value === Bool(false)) {
          state := sStart
          baud.reset
        }
      }
      is(sStart) {
        when(baud.tick) {
          state := sData
          baudCounter.reset
          paritySum := io.config.parity === eParityOdd
          when(baud.value === Bool(true)) {
            state := sIdle
          }
        }
      }
      is(sData) {
        when(baud.tick) {
          dataBuffer(baudCounter.value) := baud.value
          when(baudCounter.value === io.config.dataLength) {
            baudCounter.reset
            when(io.config.parity === eParityNone) {
              state := sStop
            } otherwise {
              state := sParity
            }
          }
        }
      }
      is(sParity) {
        when(baud.tick) {
          state := sStop
          baudCounter.reset
          when(paritySum !== baud.value) {
            state := sIdle
          }
        }
      }
      is(sStop) {
        when(baud.tick) {
          when(!baud.value) {
            state := sIdle
          }.elsewhen(baudCounter.value === toBitCount(io.config.stop)) {
            state := sIdle
            io.read.valid := Bool(true)
          }
        }
      }
    }
  }
  io.read.data := stateMachine.dataBuffer
}