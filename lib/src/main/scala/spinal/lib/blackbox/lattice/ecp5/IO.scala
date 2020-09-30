package spinal.lib.blackbox.lattice.ecp5

import spinal.core._

case class BB() extends BlackBox {
  val I, T = in Bool()
  val O = out Bool()
  val B = inout(Analog(Bool()))
}


case class TSFF() extends BlackBox{
  val TD   = in Bool()
  val SCLK = in Bool()
  val RST  = in Bool()
  val TQ =  out Bool()
}

case class IDDRX1F() extends BlackBox{
  val SCLK, RST, D = in Bool()
  val Q0, Q1 = out Bool()
  mapCurrentClockDomain(SCLK, RST)
}


object ODDRX1F{
  def apply(Q : Bool, D0 : Bool, D1 : Bool): ODDRX1F ={
    val ret = ODDRX1F()
    ret.D0 := D0
    ret.D1 := D1
    Q := ret.Q
    ret
  }
}
case class ODDRX1F() extends BlackBox{
  val SCLK, RST, D0, D1 = in Bool()
  val Q = out Bool()
  mapCurrentClockDomain(SCLK, RST)
}

case class IFS1P3BX() extends BlackBox{
  val SCLK, PD, SP, D = in Bool()
  val Q = out Bool()
  mapCurrentClockDomain(SCLK)
}

case class OFE1P3BX() extends BlackBox{
  val ECLK, PD, SP, D = in Bool()
  val Q = out Bool()
  mapCurrentClockDomain(ECLK)
}

