package spinal.lib.dsptool

import spinal.core._

/**
  * Fixnum
  *
  * @example{{{ val x = FixData(-3.785333,SQ(8,4))}}}
  * @return {{{FixData: -3.8125, Quantized by QFormat: Q(8,4,signed) }}}
  *        x.bin => 11000011
  *        x.oct =>      103
  *        x.hex =>       c3
  */
case class FixData(raw: Double,
                   q: QFormat,
                   roundType: RoundType = RoundType.ROUNDTOINF,
                   symmetric: Boolean = false )
                  (implicit button: FixSwitch = FixSwitchOn.fixButton ) {


  private val zoomRaw: Double = raw / q.resolution

  val value: Double = this.fixProcess()

  def fixTo(newQ: QFormat, round: RoundType, sym: Boolean): FixData = this.copy(this.value, newQ, round, sym)
  def fixTo(newQ: QFormat, round: RoundType): FixData = this.copy(this.value, newQ)
  def fixTo(newQ: QFormat): FixData = this.copy(this.value)

  def fixProcess(): Double ={
    button match {
      case FixSwitchOff.fixButton => raw
      case FixSwitchOn.fixButton  => {
        val rounded = this.roundType match {
          case RoundType.CEIL          => this.ceil
          case RoundType.FLOOR         => this.floor
          case RoundType.FLOORTOZERO   => this.floorToZero
          case RoundType.CEILTOINF     => this.ceilToInf
          case RoundType.ROUNDUP       => this.roundUp
          case RoundType.ROUNDDOWN     => this.roundDown
          case RoundType.ROUNDTOZERO   => this.roundToZero
          case RoundType.ROUNDTOINF    => this.roundToInf
          case RoundType.ROUNDTOEVEN   => SpinalError("RoundToEven has not been implemented yet")
          case RoundType.ROUNDTOODD    => SpinalError("RoundToOdd has not been implemented yet")
        }
        val sated = this.saturated(rounded * this.q.resolution)
        if(this.symmetric) this.symmetry(sated) else sated
      }
      case _ => SpinalError("Illegal FixSwitch!")
    }
  }

  def isSigned: Boolean   = q.signed
  def isNegative: Boolean = value < 0

  private val rawIsNegative: Boolean = raw < 0
  private val rawSign: Int           = if(rawIsNegative) -1 else 1

  private def abs: Double         = scala.math.abs(this.zoomRaw)
  private def ceil: Double        = scala.math.ceil(this.zoomRaw)
  private def floor: Double       = scala.math.floor(this.zoomRaw)
  private def floorToZero: Double = this.rawSign * scala.math.floor(this.abs)
  private def ceilToInf: Double   = this.rawSign * scala.math.ceil(this.abs)
  private def roundUp: Double     = scala.math.floor(this.zoomRaw + 0.5)
  private def roundDown: Double   = scala.math.ceil(this.zoomRaw - 0.5)
  private def roundToZero: Double = this.rawSign * scala.math.ceil(this.abs - 0.5)
  private def roundToInf: Double  = this.rawSign * scala.math.floor(this.abs + 0.5)

  private def saturated(x: Double): Double = x match {
    case d if d > q.maxValue => q.maxValue
    case d if d < q.minValue => q.minValue
    case _ => x
    }

  private def symmetry(v: Double): Double = {
    if(v == q.minValue) -q.maxValue else v
  }

  def asInt: Int          = this.value / q.resolution toInt
  def asIntPostive: Int   = if(rawIsNegative) q.capcity.toInt + this.asInt else this.asInt

  def asLong: Long        = this.value / q.resolution toLong
  def asLongPostive: Long = if(rawIsNegative) q.capcity.toLong + this.asLong else this.asLong

  def hex: String = s"%${q.alignHex}s".format(this.asLongPostive.toHexString).replace(' ','0')
  def bin: String = s"%${q.width}s".format(this.asLongPostive.toBinaryString).replace(' ','0')
  def oct: String = s"%${q.alignOct}s".format(this.asLongPostive.toOctalString).replace(' ','0')

  override def toString  = button match {
    case FixSwitchOn.fixButton  => getClass().getName().split('.').last +  s" : ${this.value}, Quantized by" +  s"\n${this.q}"
    case FixSwitchOff.fixButton => getClass().getName().split('.').last +  s" : ${this.value}, FixSwitchOff"
    case _                      => SpinalError("Illegal FixSwitch!");null
  }

  def *(right: FixData): FixData ={
    this.copy(raw = this.value * right.value, q = this.q * right.q)
  }

  def +(right: FixData): FixData ={
    this.copy(raw = this.value + right.value, q = this.q + right.q)
  }

  def -(right: FixData): FixData ={
    this.copy(raw = this.value - right.value, q = this.q - right.q)
  }

  def unary_- : FixData = this.copy(raw = -this.value, q = -this.q)

  def >>(n: Int): FixData ={
    this.copy(raw = this.value / scala.math.pow(2,n), q = this.q >> n)
  }

  def <<(n: Int): FixData ={
    this.copy(raw = this.value * scala.math.pow(2,n), q = this.q << n)
  }
}

/**
  * IntToFixData
  * @example{{{ val x = toFixData(0xFFAE,SQ(8,4))}}}
  * @return {{{FixData: -5.125, QFormat: Q(8,4,signed) }}}
  * toFixData(322111, SQ(8,4)) => FixData: -8.0,   QFormat: Q(8,4,signed)
  * toFixData(322111, UQ(8,4)) => FixData: 7.9375, QFormat: Q(8,4,unsigned)
  * toFixData(-322111,SQ(8,4)) => FixData: -8.0,   QFormat: Q(8,4,signed)
  * toFixData(-322111,UQ(8,4)) => FixData: 0,      QFormat: Q(8,4,unsigned)
  * toFixData(-0x0f,  SQ(8,4)) => FixData: -0.9375,QFormat: Q(8,4,signed)
  */
object toFixData{
  def apply(value: Int, q: QFormat): FixData = {
    (value, q.signed) match {
      case (x, true) if x >= 0 => {
        val signedValue = if (value >= q.halfCapcity) value%q.capcity - q.capcity else value
        FixData(signedValue * q.resolution, q)
      }
      case (_, true)=> {
        val signedValue =  value
        FixData(signedValue * q.resolution, q)
      }
      case (x, false) if x >= 0 => {
        val signedValue =  value
        FixData(signedValue * q.resolution, q)
      }
      case (_, false)=> {
        FixData(0, q)
      }
    }
  }
}

