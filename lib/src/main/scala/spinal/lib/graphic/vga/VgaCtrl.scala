package spinal.lib.graphic.vga

import spinal.core._
import spinal.lib._
import spinal.lib.graphic.Rgb

case class VgaTimingsHV(timingsWidth: Int) extends Bundle {
  val colorStart = UInt(timingsWidth bit)
  val colorEnd = UInt(timingsWidth bit)
  val syncStart = UInt(timingsWidth bit)
  val syncEnd = UInt(timingsWidth bit)
}

case class VgaTimings(timingsWidth: Int) extends Bundle {
  val h = VgaTimingsHV(timingsWidth)
  val v = VgaTimingsHV(timingsWidth)

  def setAs_h640_v480_r60: Unit = {
    h.syncStart := 96 - 1
    h.syncEnd := 800 - 1
    h.colorStart := 96 + 16 - 1
    h.colorEnd := 800 - 48 - 1
    v.syncStart := 2 - 1
    v.syncEnd := 525 - 1
    v.colorStart := 2 + 10 - 1
    v.colorEnd := 525 - 33 - 1
  }
}


class VgaCtrl(rgbType: Rgb, timingsWidth: Int = 12) extends Component {
  val io = new Bundle {
    val softReset = in Bool
    val timings = in(VgaTimings(timingsWidth))

    val frameStart = out Bool
    val colorLink = slave Stream (rgbType)
    val vga = master(Vga(rgbType))
  }

  case class HVArea(timingsHV: VgaTimingsHV, enable: Bool) extends Area {
    val counter = Reg(UInt(timingsWidth bit))

    val syncStart = counter === timingsHV.syncStart
    val syncEnd = counter === timingsHV.syncEnd
    val colorStart = counter === timingsHV.colorStart
    val colorEnd = counter === timingsHV.colorEnd

    when(enable) {
      counter := counter + 1
      when(colorEnd) {
        counter := 0
      }
    }

    val sync = BoolReg(syncStart, syncEnd)
    val colorEn = BoolReg(colorStart, colorEnd)

    when(io.softReset) {
      counter := 0
      sync := False
      colorEn := False
    }
  }

  val h = HVArea(io.timings.h, True)
  val v = HVArea(io.timings.v, h.syncEnd)
  val colorEn = h.colorEn && v.colorEn
  io.colorLink.ready := colorEn

  io.frameStart := v.syncEnd

  io.vga.hSync := h.sync
  io.vga.vSync := v.sync
  io.vga.color := Mux(colorEn, io.colorLink.data, rgbType.getZero)
}


object VgaCtrl {
  def main(args: Array[String]) {
    SpinalVhdl(new VgaCtrl(Rgb(8, 8, 8)))
  }
}