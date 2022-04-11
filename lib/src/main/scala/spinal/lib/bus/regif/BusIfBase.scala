package spinal.lib.bus.regif

import spinal.core._
import spinal.lib.bus.amba3.apb._
import spinal.lib.bus.misc.SizeMapping
import language.experimental.macros

import scala.collection.mutable.ListBuffer

trait BusIfBase extends Area{
  val askWrite: Bool
  val askRead: Bool
  val doWrite: Bool
  val doRead: Bool

  val readData: Bits
  val writeData: Bits
  val readError: Bool

  def readAddress(): UInt
  def writeAddress(): UInt

  def readHalt(): Unit
  def writeHalt(): Unit

  def busDataWidth: Int
  def wordAddressInc: Int = busDataWidth / 8
}

trait BusIf extends BusIfBase {
  type B <: this.type
  private val RegInsts = ListBuffer[RegInst]()
  private var regPtr: Int = 0

  def getModuleName: String
  val regPre: String

  def checkLastNA: Unit = RegInsts.foreach(_.checkLast)

  component.addPrePopTask(() => {
    readGenerator()
  })

  def newRegAt(address:Int, doc: String)(implicit symbol: SymbolName) = {
    assert(address % wordAddressInc == 0, s"located Position not align by wordAddressInc: ${wordAddressInc}")
    assert(address >= regPtr, s"located Position conflict to Pre allocated Address: ${regPtr}")
    regPtr = address + wordAddressInc
    creatReg(symbol.name, address, doc)
  }

  def newReg(doc: String)(implicit symbol: SymbolName) = {
    val res = creatReg(symbol.name, regPtr, doc)
    regPtr += wordAddressInc
    res
  }

  def creatReg(name: String, addr: Long, doc: String) = {
    val ret = new RegInst(name, addr, doc, this)
    RegInsts += ret
    ret
  }

  def newRAM(name: String, addr: Long, size: Long, doc: String) = {
    class bmi extends Bundle{
      val wr     = Bool()
      val waddr  = UInt()
      val wdata  = Bits()
      val rd     = Bool()
      val raddr  = UInt()
      val rdata  = Bits()
    }
  }

  def FIFO(doc: String)(implicit symbol: SymbolName) = {
    val  res = creatReg(symbol.name, regPtr, doc)
    regPtr += wordAddressInc
    res
  }

  @deprecated(message = "", since = "2022-12-31")
  def FactoryInterruptWithMask(regNamePre: String, triggers: Bool*): Bool = {
    triggers.size match {
      case 0 => SpinalError("There have no inputs Trigger signals")
      case x if x > busDataWidth => SpinalError(s"Trigger signal numbers exceed Bus data width ${busDataWidth}")
      case _ =>
    }
    val ENS    = newReg("Interrupt Enable Register")(SymbolName(s"${regNamePre}_ENABLES"))
    val MASKS  = newReg("Interrupt Mask   Register")(SymbolName(s"${regNamePre}_MASK"))
    val STATUS = newReg("Interrupt status Register")(SymbolName(s"${regNamePre}_STATUS"))
    val intWithMask = new ListBuffer[Bool]()
    triggers.foreach(trigger => {
      val en   = ENS.field(1 bits, AccessType.RW, doc= "int enable register")(SymbolName(s"_en"))(0)
      val mask = MASKS.field(1 bits, AccessType.RW, doc= "int mask register")(SymbolName(s"_mask"))(0)
      val stat = STATUS.field(1 bits, AccessType.RC, doc= "int status register")(SymbolName(s"_stat"))(0)
      when(trigger && en) {stat.set()}
      intWithMask +=  mask && stat
    })
    intWithMask.foldLeft(False)(_||_)
  }

  def interruptFactory(regNamePre: String, triggers: Bool*): Bool = {
    require(triggers.size > 0)
    val groups = triggers.grouped(this.busDataWidth).toList
    val ret = groups.zipWithIndex.map{case (trigs, i) =>
      val namePre = if (groups.size == 1) regNamePre else regNamePre + i
      int_RFMS(namePre, trigs:_*)
    }
    val intr = Vec(ret).asBits.orR
    val regNamePre_ = if (regNamePre != "") regNamePre+"_" else ""
    intr.setName(regNamePre_ + "intr")
    intr
  }

  def interruptLevelFactory(regNamePre: String, levels: Bool*): Bool = {
    require(levels.size > 0)
    val groups = levels.grouped(this.busDataWidth)
    val ret = groups.zipWithIndex.map{case (trigs, i) =>
      val namePre = if (groups.size == 1) regNamePre else regNamePre + i
      int_MS(namePre, trigs:_*)
    }
    val intr = Vec(ret).asBits.orR
    val regNamePre_ = if (regNamePre != "") regNamePre+"_" else ""
    intr.setName(regNamePre_ + "intr")
    intr
  }
  /*
  interrupt with Raw/Force/Mask/Status Register Interface
  * */
  protected def int_RFMS(regNamePre: String, triggers: Bool*): Bool = {
    val regNamePre_ = if (regNamePre != "") regNamePre+"_" else ""
    require(triggers.size <= this.busDataWidth )
    val RAW    = this.newReg("Interrupt Raw status Register\n set when event \n clear when write 1")(SymbolName(s"${regNamePre_}INT_RAW"))
    val FORCE  = this.newReg("Interrupt Force  Register\n for SW debug use")(SymbolName(s"${regNamePre_}INT_FORCE"))
    val MASK   = this.newReg("Interrupt Mask   Register\n1: int off\n0: int open\n default 1, int off")(SymbolName(s"${regNamePre_}INT_MASK"))
    val STATUS = this.newReg("Interrupt status Register\n the final int out")(SymbolName(s"${regNamePre_}INT_STATUS"))
    val ret = triggers.map{ event =>
      val nm = event.getPartialName()
      val force = FORCE.field(1 bit, AccessType.RW,   resetValue = 0, doc = s"force, default 0" )(SymbolName(s"${nm}_force")).lsb
      val raw   = RAW.field(1 bit, AccessType.W1C,    resetValue = 0, doc = s"raw, default 0" )(SymbolName(s"${nm}_raw")).lsb
      val mask  = MASK.field(1 bit, AccessType.RW,    resetValue = 1, doc = s"mask, default 1, int off" )(SymbolName(s"${nm}_mask")).lsb
      val status= STATUS.field(1 bit, AccessType.RO,  resetValue = 0, doc = s"stauts default 0" )(SymbolName(s"${nm}_status")).lsb
      raw.setWhen(event)
      status := (raw || force) && (!mask)
      status
    }.reduceLeft(_ || _)
    ret.setName(s"${regNamePre_.toLowerCase()}intr", weak = true)
    ret
  }

  /*
    interrupt with Force/Mask/Status Register Interface
    * */
  protected def int_RMS(regNamePre: String, triggers: Bool*): Bool = {
    val regNamePre_ = if (regNamePre != "") regNamePre+"_" else ""
    require(triggers.size <= this.busDataWidth )
    val RAW    = this.newReg("Interrupt Raw status Register\n set when event \n clear when write 1")(SymbolName(s"${regNamePre_}INT_RAW"))
    val MASK   = this.newReg("Interrupt Mask   Register\n1: int off\n0: int open\n default 1, int off")(SymbolName(s"${regNamePre_}INT_MASK"))
    val STATUS = this.newReg("Interrupt status Register\n the final int out")(SymbolName(s"${regNamePre_}INT_STATUS"))
    val ret = triggers.map{ event =>
      val nm = event.getName()
      val raw   = RAW.field(1 bit, AccessType.W1C,    resetValue = 0, doc = s"raw, default 0" )(SymbolName(s"${nm}_raw")).lsb
      val mask  = MASK.field(1 bit, AccessType.RW,    resetValue = 1, doc = s"mask, default 1, int off" )(SymbolName(s"${nm}_mask")).lsb
      val status= STATUS.field(1 bit, AccessType.RO,  resetValue = 0, doc = s"stauts default 0" )(SymbolName(s"${nm}_status")).lsb
      raw.setWhen(event)
      status := raw && (!mask)
      status
    }.reduceLeft(_ || _)
    ret.setName(s"${regNamePre_.toLowerCase()}intr")
    ret
  }

  /*
    interrupt with Mask/Status Register Interface
    * */
  protected def int_MS(regNamePre: String, int_levels: Bool*): Bool = {
    val regNamePre_ = if (regNamePre != "") regNamePre+"_" else ""
    require(int_levels.size <= this.busDataWidth )
    val MASK   = this.newReg("Interrupt Mask   Register\n1: int off\n0: int open\n default 1, int off")(SymbolName(s"${regNamePre_}INT_RAW"))
    val STATUS = this.newReg("Interrupt status Register\n the final int out")(SymbolName(s"${regNamePre_}INT_STATUS"))
    val ret = int_levels.map{ level =>
      val nm = level.getName()
      val mask  = MASK.field(1 bit, AccessType.RW,    resetValue = 1, doc = s"mask" )(SymbolName(s"${nm}_mask")).lsb
      val status= STATUS.field(1 bit, AccessType.RO,  resetValue = 0, doc = s"stauts" )(SymbolName(s"${nm}_status")).lsb
      status := level && (!mask)
      status
    }.reduceLeft(_ || _)
    ret.setName(s"${regNamePre_.toLowerCase()}intr")
    ret
  }

  def accept(vs : BusIfVisitor) = {
    checkLastNA

    vs.begin(busDataWidth)

    for(reg <- RegInsts) {
      reg.accept(vs)
    }

    vs.end()
  }

  private def readGenerator() = {
    when(askRead){
      switch (readAddress()) {
        RegInsts.foreach{(reg: RegInst) =>
          is(reg.addr){
            if(!reg.allIsNA){
              readData  := reg.readBits
              readError := Bool(reg.readErrorTag)
            }
          }
        }
        default{
          readData  := 0
          readError := True
        }
      }
    }
  }
}
