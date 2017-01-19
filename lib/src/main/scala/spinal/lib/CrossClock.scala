package spinal.lib

import spinal.core._


object BufferCC {
  def apply[T <: Data](input: T, init: T = null, bufferDepth: Int = 2): T = {
    val c = new BufferCC(input, init != null, bufferDepth)
    c.io.dataIn := input
    if(init != null) c.io.initial := init

    val ret = cloneOf(c.io.dataOut)
    ret := c.io.dataOut
    return ret
  }
}

class BufferCC[T <: Data](dataType: T, withInit : Boolean, bufferDepth: Int) extends Component {
  assert(bufferDepth >= 1)

  val io = new Bundle {
    val initial = if(!withInit) null.asInstanceOf[T] else in(cloneOf(dataType))
    val dataIn = in(cloneOf(dataType))
    val dataOut = out(cloneOf(dataType))
  }

  val buffers = Vec(Reg(dataType, io.initial),bufferDepth)

  buffers(0) := io.dataIn
  buffers(0).addTag(crossClockDomain)
  for (i <- 1 until bufferDepth) {
    buffers(i) := buffers(i - 1)
    buffers(i).addTag(crossClockBuffer)

  }

  io.dataOut := buffers.last
}


object PulseCCByToggle {
  def apply(input: Bool, clockIn: ClockDomain, clockOut: ClockDomain): Bool = {
    val c = new PulseCCByToggle(clockIn,clockOut)
    c.io.pulseIn := input
    return c.io.pulseOut
  }
}


class PulseCCByToggle(clockIn: ClockDomain, clockOut: ClockDomain) extends Component{
  val io = new Bundle{
    val pulseIn = in Bool
    val pulseOut = out Bool
  }

  val inArea = new ClockingArea(clockIn) {
    val target = RegInit(False)
    when(io.pulseIn) {
      target := !target
    }
  }

  val outArea = new ClockingArea(clockOut) {
    val target = BufferCC(inArea.target, False)
    val hit    = RegInit(False);

    when(target =/= hit) {
      hit := !hit
    }

    io.pulseOut := (target =/= hit)
  }
}


object ResetCtrl{
  /**
   *
   * @param input Input reset signal
   * @param clockDomain ClockDomain which will use the synchronised reset.
   * @param inputPolarity (HIGH/LOW)
   * @param outputPolarity (HIGH/LOW)
   * @param bufferDepth Number of register stages used to avoid metastability (default=2)
   * @return Filtred Bool which is asynchronously asserted synchronously deaserted
   */
  def asyncAssertSyncDeassert(input : Bool,
                              clockDomain : ClockDomain,
                              inputPolarity : Polarity = HIGH,
                              outputPolarity : Polarity = null, //null => inferred from the clockDomain
                              bufferDepth : Int = 2) : Bool = {
    val samplerCD = ClockDomain(
      clock = clockDomain.clock,
      reset = input,
      clockEnable = clockDomain.clockEnable,
      config = clockDomain.config.copy(
        resetKind = ASYNC,
        resetActiveLevel = inputPolarity
      )
    )

    val solvedOutputPolarity = if(outputPolarity == null) clockDomain.config.resetActiveLevel else outputPolarity
    samplerCD(BufferCC(
      input       = if(solvedOutputPolarity == HIGH) False else True,
      init        = if(solvedOutputPolarity == HIGH) True  else False,
      bufferDepth = bufferDepth)
    )
  }

  /**
   * As asyncAssertSyncDeassert but directly drive the clockDomain reset with the syncronised signal.
   */
  def asyncAssertSyncDeassertDrive(input : Bool,
                                   clockDomain : ClockDomain,
                                   inputPolarity : Polarity = HIGH,
                                   outputPolarity : Polarity = null, //null => inferred from the clockDomain
                                   bufferDepth : Int = 2) : Unit = clockDomain.reset := asyncAssertSyncDeassert(
    input = input ,
    clockDomain = clockDomain ,
    inputPolarity = inputPolarity ,
    outputPolarity = outputPolarity ,
    bufferDepth = bufferDepth
  )
}