package spinal.tester.generator.regif

import spinal.core._
import spinal.core.sim._
import spinal.lib._
import spinal.lib.bus.amba3.ahblite.AhbLite3
import spinal.lib.bus.amba3.apb.{Apb3, Apb3Config}
import spinal.lib.bus.amba3.apb.sim.Apb3Driver
import spinal.lib.bus.amba4.apb._
import spinal.lib.bus.amba4.apb.sim.Apb4Driver
import spinal.lib.bus.regif.AccessType._
import spinal.lib.bus.regif._
import spinal.lib.bus.wishbone.Wishbone


class RegIfBasicAccessTest(busname: String) extends Component{
  val (bus, busif) = busname match{
    case "apb3" => {
      val bus = slave(Apb3(Apb3Config(32, 32)))
      bus -> BusInterface(bus, (0x000, 4 MiB), 0, regPre = "AP")
    }
    case "apb4" => {
      val bus = slave(Apb4(Apb4Config(32, 32)))
      bus -> BusInterface(bus, (0x000, 4 MiB), 0, regPre = "AP")
    }
    case _ => SpinalError("not support yet")
  }

  val reg_ro      = busif.newReg(doc = "RO    ").field(Bits(32 bit), RO   , 0x7788abcd, doc = "ro   ")
  val reg_rw      = busif.newReg(doc = "RW    ").field(Bits(32 bit), RW   , 0x77880002, doc = "rw   ").asOutput()
  val reg_rc      = busif.newReg(doc = "RC    ").field(Bits(32 bit), RC   , 0x77880003, doc = "rc   ").asOutput()
  val reg_rs      = busif.newReg(doc = "RS    ").field(Bits(32 bit), RS   , 0x77880004, doc = "rs   ").asOutput()
  val reg_wrc     = busif.newReg(doc = "WRC   ").field(Bits(32 bit), WRC  , 0x77880005, doc = "wrc  ").asOutput()
  val reg_wrs     = busif.newReg(doc = "WRS   ").field(Bits(32 bit), WRS  , 0x77880006, doc = "wrs  ").asOutput()
  val reg_wc      = busif.newReg(doc = "WC    ").field(Bits(32 bit), WC   , 0x77880007, doc = "wc   ").asOutput()
  val reg_ws      = busif.newReg(doc = "WS    ").field(Bits(32 bit), WS   , 0x77880008, doc = "ws   ").asOutput()
  val reg_wsrc    = busif.newReg(doc = "WSRC  ").field(Bits(32 bit), WSRC , 0x77880009, doc = "wsrc ").asOutput()
  val reg_wcrs    = busif.newReg(doc = "WCRS  ").field(Bits(32 bit), WCRS , 0x7788000a, doc = "wcrs ").asOutput()
  val reg_w1c     = busif.newReg(doc = "W1C   ").field(Bits(32 bit), W1C  , 0x7788000b, doc = "w1c  ").asOutput()
  val reg_w1s     = busif.newReg(doc = "W1S   ").field(Bits(32 bit), W1S  , 0x7788000c, doc = "w1s  ").asOutput()
  val reg_w1t     = busif.newReg(doc = "W1T   ").field(Bits(32 bit), W1T  , 0x7788000d, doc = "w1t  ").asOutput()
  val reg_w0c     = busif.newReg(doc = "W0C   ").field(Bits(32 bit), W0C  , 0x7788000e, doc = "w0c  ").asOutput()
  val reg_w0s     = busif.newReg(doc = "W0S   ").field(Bits(32 bit), W0S  , 0x7788000f, doc = "w0s  ").asOutput()
  val reg_w0t     = busif.newReg(doc = "W0T   ").field(Bits(32 bit), W0T  , 0x77880011, doc = "w0t  ").asOutput()
  val reg_w1src   = busif.newReg(doc = "W1SRC ").field(Bits(32 bit), W1SRC, 0x77880012, doc = "w1src").asOutput()
  val reg_w1crs   = busif.newReg(doc = "W1CRS ").field(Bits(32 bit), W1CRS, 0x77880013, doc = "w1crs").asOutput()
  val reg_w0src   = busif.newReg(doc = "W0SRC ").field(Bits(32 bit), W0SRC, 0x77880014, doc = "w0src").asOutput()
  val reg_w0crs   = busif.newReg(doc = "W0CRS ").field(Bits(32 bit), W0CRS, 0x77880015, doc = "w0crs").asOutput()
  val reg_wo      = busif.newReg(doc = "WO    ").field(Bits(32 bit), WO   , 0x77880016, doc = "wo   ").asOutput()
  val reg_woc     = busif.newReg(doc = "WOC   ").field(Bits(32 bit), WOC  , 0x77880017, doc = "woc  ").asOutput()
  val reg_wos     = busif.newReg(doc = "WOS   ").field(Bits(32 bit), WOS  , 0x77880018, doc = "wos  ").asOutput()
  val reg_w1      = busif.newReg(doc = "W1    ").field(Bits(32 bit), W1   , 0x77880019, doc = "w1   ").asOutput()
  val reg_wo1     = busif.newReg(doc = "WO1   ").field(Bits(32 bit), WO1  , 0x7788001a, doc = "wo1  ").asOutput()
  val reg_na      = busif.newReg(doc = "NA    ").field(Bits(32 bit), NA   , 0x7788001b, doc = "na   ").asOutput()
  val reg_w1p     = busif.newReg(doc = "W1P   ").field(Bits(32 bit), W1P  , 0x7788001c, doc = "w1p  ").asOutput()
  val reg_w0p     = busif.newReg(doc = "W0P   ").field(Bits(32 bit), W0P  , 0x7788001d, doc = "w0p  ").asOutput()
  val reg_hsrw    = busif.newReg(doc = "HSRW  ").field(Bits(32 bit), HSRW , 0x7788001e, doc = "hsrw ").asOutput()
                    busif.newReg(doc = "ROV   ").field(Bits(32 bit), ROV  , 0x77885566, doc = "rov")
  val reg_bmsc_2a = busif.newReg(doc = "BMSC-A").field(Bits(32 bit), W1S  , 0x3fedca98, doc = "32 bit write 1 set").asOutput()
                    busif.newReg(doc = "BMSC-B").parasiteField(reg_bmsc_2a, W1C  , 0         , doc = "32 bit write 1 clear") //two address share one reg
  val reg_bmsc_4a = busif.newReg(doc = "BMSC-A").field(Bits(32 bit),  RW  , 0x2bcd1234, doc = "32 bit RW").asOutput()
                    busif.newReg(doc = "BMSC-B").parasiteField(reg_bmsc_2a, W1C  , 0, doc = "32 bit write 1 set")   //4 address share one reg
                    busif.newReg(doc = "BMSC-C").parasiteField(reg_bmsc_2a, W1C  , 0, doc = "32 bit write 1 clear") //4 address share one reg
  val reg_bmsc_4ar= busif.newReg(doc = "BMSC-D").field(Bits(32 bit), RO   , 0, doc = "32 bit read only")     //4 address share one reg
  reg_bmsc_4ar := reg_bmsc_4a
  reg_ro := 0

  val refdata = List("12345678".asHex, "5a5a5a5a".asHex, "ffffffff".asHex, "00000000".asHex, "37abcdef".asHex, "11111111".asHex, "35af0782".asHex)
  def write(addr: Long, data: BigInt, strb: BigInt = 0xff): Unit = {
    bus match {
      case bs: Apb3     => Apb3Driver(bs, this.clockDomain).write(addr, data)
      case bs: Apb4     => Apb4Driver(bs, this.clockDomain).write(addr, data, strb)
      case bs: AhbLite3 => SpinalError("AhbLIte3 regif test not support yet")
      case bs: Wishbone => SpinalError("Wishbon  regif test not support yet")
    }
  }
  def read(addr: Long): BigInt = {
    bus match {
      case bs: Apb3 => Apb3Driver(bs, this.clockDomain).read(addr)
      case bs: Apb4 => Apb4Driver(bs, this.clockDomain).read(addr)
      case bs: AhbLite3 => SpinalError("AhbLIte3 regif test not support yet")
      case bs: Wishbone => SpinalError("Wishbon  regif test not support yet")
    }
  }

  def siminit() = {
    this.clockDomain.forkStimulus(2)
    this.clockDomain.waitSampling(100)
  }
  def regression() = {
    siminit()
    tc00_ro   (0x0000)
    tc01_rw   (0x0004)
    tc02_rc   (0x0008)
    tc03_rs   (0x000c)
    tc04_wrc  (0x0010)
    tc05_wrs  (0x0014)
    tc06_wc   (0x0018)
    tc07_ws   (0x001c)
    tc08_wsrc (0x0020)
    tc09_wcrs (0x0024)
    tc10_w1c  (0x0028)
    tc11_w1s  (0x002c)
    tc12_w1t  (0x0030)
    tc13_w0c  (0x0034)
    tc14_w0s  (0x0038)
    tc15_w0t  (0x003c)
    tc16_w1src(0x0040)
    tc17_w1crs(0x0044)
    tc18_w0src(0x0048)
    tc19_w0crs(0x004c)
    tc20_wo   (0x0050)
    tc21_woc  (0x0054)
    tc22_wos  (0x0058)
    tc23_w1   (0x005c)
    tc24_wo1  (0x0060)
    tc25_na   (0x0064)
    tc26_w1p  (0x0068)
    tc27_w0p  (0x006c)
    tc28_hsrw (0x0070)
    tc29_rov  (0x0074)
    tc30_bmsc_2a (0x0078)
    tc31_bmsc_4a (0x007c)
  }
  def tc00_ro   (addr: Long) = {
    SpinalInfo("RS - [TBD-Warning] ")
  }
  def tc01_rw   (addr: Long) = {
    def test(data: BigInt) = {
      write(addr, data)
      val rdata = read(addr)
      assert(data == rdata, s"0x${data.hexString(32)} != 0x${rdata.hexString(32)}, RW test failed")
    }
    refdata.foreach(test(_))
  }
  def tc02_rc   (addr: Long) = {
    def test(data: BigInt) = {
      write(addr, data)
      val rdata = read(addr)
      assert(rdata == 0, s"0x${rdata.hexString(32)} != 0x00000000, RC test failed")
    }
    refdata.foreach(test(_))
    SpinalInfo("RC - test TBA-pass")
  }
  def tc03_rs   (addr: Long) = {
    def test(data: BigInt) = {
      write(addr, data)
      val rdata = read(addr)
      assert(rdata == BigInt("FFFFFFFF", 16), s"0x${rdata.hexString(32)} != 0xFFFFFFFF, RS test failed")
    }
    refdata.foreach(test(_))
    SpinalInfo("RS - test TBA-pass")
  }
  def tc04_wrc  (addr: Long) = {
    def test(data: BigInt) = {
      write(addr, data)
      val rdata = read(addr)
      assert(rdata == 0, s"0x${rdata.hexString(32)} != 0x00000000, WRC test failed")
    }
    refdata.foreach(test(_))
    SpinalInfo("WRC - test pass")
  }
  def tc05_wrs  (addr: Long) = {
    def test(data: BigInt) = {
      write(addr, data)
      val rdata = read(addr)
      assert(rdata == BigInt("FFFFFFFF", 16), s"0x${rdata.hexString(32)} != 0xFFFFFFFF, WRS test failed")
    }
    refdata.foreach(test(_))
    SpinalInfo("WRC - test pass")
  }
  def tc06_wc   (addr: Long) = {
    val rdata = read(addr)
    assert(rdata != 0, s"reset value not empty before write")
    write(addr, 0xabcdef53)
    assert(rdata == 0, s"0x${rdata.hexString(32)} != 0x00000000, WC test failed")
    SpinalInfo("WC - test TBA-pass")
  }
  def tc07_ws   (addr: Long) = {
    val rdata = read(addr)
    assert(rdata != BigInt("FFFFFFFF", 16), s"reset value not high before write")
    write(addr, 0xabcdef53)
    assert(rdata == BigInt("FFFFFFFF", 16), s"0x${rdata.hexString(32)} != 0xFFFFFFFF, WS test failed")
    SpinalInfo("WC - test TBA-pass")
  }
  def tc08_wsrc (addr: Long) = {
    val rdata = read(addr)
    assert(rdata != 0, s"reset value not empty before write")
    write(addr, 0xabcdef53)
    assert(rdata == 0, s"0x${rdata.hexString(32)} != 0x00000000, WC test failed")
    SpinalInfo("WSRC - test TBA-pass")
  }
  def tc09_wcrs (addr: Long) = {
    val rdata = read(addr)
    assert(rdata != BigInt("FFFFFFFF", 16), s"reset value not high before write")
    write(addr, 0xabcdef53)
    assert(rdata == BigInt("FFFFFFFF", 16), s"0x${rdata.hexString(32)} != 0xFFFFFFFF, WS test failed")
    SpinalInfo("WCRS - test TBA-pass")
  }
  def tc10_w1c  (addr: Long) = {
//    val TV = List(
//      BigInt("aaaaaaaa", 16) -> BigInt("55555555", 16),
//      BigInt("55555555", 16) -> BigInt("aaaaaaaa", 16),
//      BigInt("aaaa5555", 16) -> BigInt("5555aaaa", 16),
//      BigInt("00000000", 16) -> BigInt("00000000", 16),
//      BigInt("3333cccc", 16) -> BigInt("cccc3333", 16),
//    )
//    def test(t: (BigInt, BigInt)) = {
//      write(addr, t._1)
//      val rdata = read(addr)
//      assert(rdata == t._2, s"0x${rdata.hexString(32)} != 0x${t._2.hexString(32)}, W1C test failed")
//    }
//    TV.foreach(test)
    SpinalInfo("W1C - [TBD-Warning] ")
  }
  def tc11_w1s  (addr: Long) = {
//    val TV = List(
//      BigInt("aaaaaaaa", 16) -> BigInt("55555555", 16),
//      BigInt("55555555", 16) -> BigInt("aaaaaaaa", 16),
//      BigInt("aaaa5555", 16) -> BigInt("5555aaaa", 16),
//      BigInt("00000000", 16) -> BigInt("00000000", 16),
//      BigInt("3333cccc", 16) -> BigInt("cccc3333", 16),
//    )
//    def test(t: (BigInt, BigInt)) = {
//      write(addr, t._1)
//      val rdata = read(addr)
//      assert(rdata == t._2, s"0x${rdata.hexString(32)} != 0x${t._2.hexString(32)}, W1C test failed")
//    }
//    TV.foreach(test)
    SpinalInfo("W1S - [TBD-Warning] ")
  }
  def tc12_w1t  (addr: Long) = {
    //det on reset value
//    val rdata = read(addr)
//    assert(rdata != BigInt("FFFFFFFF", 16), s"reset value not high before write")
//    write(addr, 0xabcdef53)
//    assert(rdata == BigInt("FFFFFFFF", 16), s"0x${rdata.hexString(32)} != 0xFFFFFFFF, WS test failed")
    SpinalInfo("W1S - [TBD-Warning] ")
  }
  def tc13_w0c  (addr: Long) = {
    SpinalInfo("WOC - [TBD-Warning] ")
  }
  def tc14_w0s  (addr: Long) = {
    SpinalInfo("WOS - [TBD-Warning] ")
  }
  def tc15_w0t  (addr: Long) = {
    SpinalInfo("W0T - [TBD-Warning] ")
  }
  def tc16_w1src(addr: Long) = {
    SpinalInfo("W1SRC - [TBD-Warning] ")
  }
  def tc17_w1crs(addr: Long) = {
    SpinalInfo("W1CRS - [TBD-Warning] ")
  }
  def tc18_w0src(addr: Long) = {
    SpinalInfo("W0SRC - [TBD-Warning] ")
  }
  def tc19_w0crs(addr: Long) = {
    SpinalInfo("W1CRS - [TBD-Warning] ")
  }
  def tc20_wo   (addr: Long) = {
    SpinalInfo("WO - [TBD-Warning] ")
  }
  def tc21_woc  (addr: Long) = { }
  def tc22_wos  (addr: Long) = { }
  def tc23_w1   (addr: Long) = { }
  def tc24_wo1  (addr: Long) = { }
  def tc25_na   (addr: Long) = { }
  def tc26_w1p  (addr: Long) = { }
  def tc27_w0p  (addr: Long) = { }
  def tc28_hsrw (addr: Long) = { }
  def tc29_rov  (addr: Long) = { }
  def tc30_bmsc_2a (addr: Long) = { }
  def tc31_bmsc_4a (addr: Long) = { }
}


//object utils extends App{
//  val spinalConfig = SpinalConfig(
//    defaultConfigForClockDomains = ClockDomainConfig(clockEdge = RISING,
//      resetKind = ASYNC,
//      resetActiveLevel = LOW
//    ),
//    defaultClockDomainFrequency = FixedFrequency(200 MHz),
//    targetDirectory = "./out/rtl/",
//    headerWithDate = true,
//    inlineConditionalExpression = true,
//    oneFilePerComponent = false,
//    nameWhenByFile = false,
//    removePruned = true,
//    anonymSignalPrefix = "t",
//    mergeAsyncProcess = true)
//
//  val simcfg = SpinalSimConfig().withConfig(spinalConfig)
//}

object Apb4test extends App{
  val spinalConfig = SpinalConfig(
    defaultConfigForClockDomains = ClockDomainConfig(clockEdge = RISING,
      resetKind = ASYNC,
      resetActiveLevel = LOW
    ),
    defaultClockDomainFrequency = FixedFrequency(200 MHz),
    targetDirectory = "./out/rtl/",
    headerWithDate = true,
    inlineConditionalExpression = true,
    oneFilePerComponent = false,
    nameWhenByFile = false,
    removePruned = true,
    anonymSignalPrefix = "t",
    mergeAsyncProcess = true)

  val simcfg = SpinalSimConfig().withConfig(spinalConfig)
  simcfg
    .compile(new RegIfBasicAccessTest("apb4"))
    .doSimUntilVoid("regif_apb4_test"){ dut =>
      dut.regression()
      simSuccess()
    }
}
object Apb3test extends App{
  SpinalSimConfig().compile(new RegIfBasicAccessTest("apb3"))
    .doSimUntilVoid("regif_apb3_test"){ dut =>
      dut.regression()
      simSuccess()
    }
}