package spinal.lib.memory.sdram.xdr

import spinal.core._
import spinal.lib._
import spinal.lib.bus.bmb.{Bmb, BmbParameter}
import spinal.lib.bus.misc.BusSlaveFactory
import spinal.lib.io.TriState

case class MemoryLayout(bankWidth : Int,
                        columnWidth : Int,
                        rowWidth : Int,
                        dataWidth : Int,
                        withDqs : Boolean,
                        burstLength : Int){
  def wordWidth = dataWidth*burstLength
  def bytePerDq = dataWidth/8
  def bytePerWord = wordWidth/8
  def wordAddressWidth = bankWidth + columnWidth + rowWidth
  def byteAddressWidth = bankWidth + columnWidth + rowWidth + log2Up(bytePerWord)
  def chipAddressWidth = Math.max(columnWidth,rowWidth)
  def bankCount = 1 << bankWidth
  def capacity = BigInt(1) << byteAddressWidth
  def columnSize = 1 << columnWidth
}


case class PhyLayout(phaseCount : Int,
                     outputLatency : Int,
                     inputLatency : Int,
                     ml : MemoryLayout){
  def beatWidth = phaseCount * ml.dataWidth
  def beatCount = ml.burstLength / phaseCount
}
case class Timing()
case class Timings(      bootRefreshCount : Int, // Number of refresh command done in the boot sequence
                         tPOW  : TimeNumber,     // Powerup time
                         tREF  : TimeNumber,     // Refresh Cycle Time (that cover all row)
//                         tRC   : TimeNumber,     // Command Period (ACT to ACT)   Per bank
                         tRFC  : TimeNumber,     // Command Period (REF to REF)   Per bank
                         tRAS  : TimeNumber,     // Command Period (ACT to PRE)   Per bank
                         tRP   : TimeNumber,     // Command Period (PRE to ACT) Per bank
                         tRCD  : TimeNumber,     // ACT To READ / WRITE Command Delay Time per bank
                         cMRD  : Int,            // Mode Register Program Time
                         tWR   : TimeNumber,     // WRITE recovery time (WRITE to PRE) per bank
                         cWR   : Int)            // WRITE recovery cycle (WRITE to PRE) per bank
//tWTR //WRITE to READ cross bank
//tCCD //CAS to CAS cross bank
//tRRD //Active to Active cross bank
//tFAW //Four ACTIVATE windows
//RTP READ to PRE

//TODO tFAW
//TODO ctrl lock

case class Ddr3(l : MemoryLayout) extends Bundle with IMasterSlave{
  val ADDR  = Bits(l.chipAddressWidth bits)
  val BA    = Bits(l.bankWidth bits)
  val DQ    = TriState(Bits(l.dataWidth bits))
  val DQS   = TriState(Bits(l.bytePerDq bits))
  val DM    = Bits(l.bytePerDq bits)
  val CASn  = Bool
  val CKE   = Bool
  val CSn   = Bool
  val RASn  = Bool
  val WEn   = Bool
  val ODT   = Bool
  val RESETn   = Bool

  override def asMaster(): Unit = {
    out(ADDR,BA,CASn,CKE,CSn,DM,RASn,WEn,ODT,RESETn)
    master(DQ)
  }
}


case class Sdr(ml : MemoryLayout) extends Bundle with IMasterSlave{
  val ADDR  = Bits(ml.chipAddressWidth bits)
  val BA    = Bits(ml.bankWidth bits)
  val DQ    = TriState(Bits(ml.dataWidth bits))
  val DQM   = Bits(ml.bytePerWord bits)
  val CASn  = Bool
  val CKE   = Bool
  val CSn   = Bool
  val RASn  = Bool
  val WEn   = Bool

  override def asMaster(): Unit = {
    out(ADDR,BA,CASn,CKE,CSn,DQM,RASn,WEn)
    master(DQ)
  }
}

case class SdramXdrPhyCtrlPhase(pl : PhyLayout) extends Bundle with IMasterSlave{
  val CASn  = Bool()
  val CKE   = Bool()
  val CSn   = Bool()
  val RASn  = Bool()
  val WEn   = Bool()

  val DM    = Bits(pl.ml.bytePerDq bits)
  val DQw, DQr = Bits(pl.ml.dataWidth bits)

  override def asMaster(): Unit = {
    out(CASn,CKE,CSn,DM,RASn,WEn)
    out(DQw)
    in(DQr)
  }
}

case class SdramXdrPhyCtrl(pl : PhyLayout) extends Bundle with IMasterSlave{
  val phases = Vec(SdramXdrPhyCtrlPhase(pl), pl.phaseCount)
  val ADDR  = Bits(pl.ml.chipAddressWidth bits)
  val BA    = Bits(pl.ml.bankWidth bits)
  val DQe = Bool()
  val DQS = pl.ml.withDqs generate new Bundle {
    val preamble = Bool()
    val active = Bool()
    val postamble = Bool()
  }
  override def asMaster(): Unit = {
    phases.foreach(master(_))
    out(ADDR,BA,DQe)
    if(pl.ml.withDqs) out(DQS)
  }
}

abstract class Phy[T <: Data with IMasterSlave](val pl : PhyLayout) extends Component{
  def MemoryBus() : T
  def driveFrom(mapper : BusSlaveFactory) : Unit

  val io = new Bundle {
    val ctrl = slave(SdramXdrPhyCtrl(pl))
    val memory = master(MemoryBus())
  }
}


object SdrInferedPhy{
  def memoryLayoutToPhyLayout(ml : MemoryLayout) = PhyLayout(
    phaseCount = 1,
    outputLatency = 1,
    inputLatency = 1,
    ml = ml
  )
}

case class SdrInferedPhy(ml : MemoryLayout) extends Phy[Sdr](SdrInferedPhy.memoryLayoutToPhyLayout(ml)){
  require(!ml.withDqs)

  override def MemoryBus(): Sdr = Sdr(ml)
  override def driveFrom(mapper: BusSlaveFactory): Unit = {}

  io.memory.ADDR  := RegNext(io.ctrl.ADDR)
  io.memory.BA    := RegNext(io.ctrl.BA  )
  io.memory.DQM   := RegNext(io.ctrl.phases(0).DM  )
  io.memory.CASn  := RegNext(io.ctrl.phases(0).CASn)
  io.memory.CKE   := RegNext(io.ctrl.phases(0).CKE )
  io.memory.CSn   := RegNext(io.ctrl.phases(0).CSn )
  io.memory.RASn  := RegNext(io.ctrl.phases(0).RASn)
  io.memory.WEn   := RegNext(io.ctrl.phases(0).WEn )

  io.memory.DQ.writeEnable  := RegNext(io.ctrl.DQe)
  io.memory.DQ.write        := RegNext(io.ctrl.phases(0).DQw )
  io.ctrl.phases(0).DQr     := RegNext(io.memory.DQ.read )
}

case class CorePortParameter( contextWidth : Int)

case class CorePort(cpp : CorePortParameter, cpa : CoreParameterAggregate) extends Bundle with IMasterSlave{
  val cmd = Stream(Fragment(CoreCmd(cpp, cpa)))
  val rsp = Stream(Fragment(CoreRsp(cpp, cpa)))

  override def asMaster(): Unit = {
    master(cmd)
    slave(rsp)
  }
}

case class CoreCmd(cpp : CorePortParameter, cpa : CoreParameterAggregate) extends Bundle{
  import cpa._
  val write = Bool()
  val address = UInt(ml.byteAddressWidth bits)
  val data = Bits(pl.beatWidth bits)
  val mask = Bits(pl.beatWidth/8 bits)
  val context = Bits(cpp.contextWidth bits)
}
case class CoreRsp(cpp : CorePortParameter, cpa : CoreParameterAggregate) extends Bundle{
  val data = Bits(cpa.pl.beatWidth bits)
  val context = Bits(cpp.contextWidth bits)
}

case class SdramAddress(ml : MemoryLayout) extends Bundle {
  val column = UInt(ml.columnWidth bits)
  val bank   = UInt(ml.bankWidth bits)
  val row    = UInt(ml.rowWidth bits)
}

case class CoreConfig(cpa : CoreParameterAggregate) extends Bundle {
  import cpa._

  val commandPhase = UInt(log2Up(pl.phaseCount) bits)
  val writeLatency = UInt(log2Up(cp.writeLatencies.size) bits)
  val readLatency = UInt(log2Up(cp.readLatencies.size) bits)
  val RFC, RAS, RP, WR, RCD, WTR, CCD, RTP = UInt(cp.timingWidth bits)
  val REF = UInt(cp.refWidth bits)

  def driveFrom(mapper : BusSlaveFactory) = new Area {
    mapper.drive(commandPhase, 0x00, 0)
    mapper.drive(writeLatency, 0x00, 16)
    mapper.drive(readLatency,  0x00, 24)
    mapper.drive(REF, 0x10,  0)
    mapper.drive(RFC, 0x20,  8)
    mapper.drive(RAS, 0x20, 16)
    mapper.drive(RP , 0x20, 24)
    mapper.drive(WR , 0x20,  0)
    mapper.drive(RCD, 0x24,  8)
    mapper.drive(WTR, 0x24, 16)
    mapper.drive(CCD, 0x24, 24)
    mapper.drive(RTP, 0x28,  0)
  }
}

case class CoreParameter(timingWidth : Int,
                         refWidth : Int,
                         writeLatencies : List[Int],
                         readLatencies : List[Int])

object FrontendCmdOutputKind extends SpinalEnum{
  val READ, WRITE, ACTIVE, PRECHARGE, REFRESH = newElement()
}
case class CoreTask(cpa : CoreParameterAggregate) extends Bundle {
  import cpa._

  val kind = FrontendCmdOutputKind()
  val all = Bool()
  val address = SdramAddress(pl.ml)
  val data = Bits(pl.beatWidth bits)
  val mask = Bits(pl.beatWidth/8 bits)
  val source = UInt(log2Up(cpp.size) bits)
  val context = Bits(backendContextWidth bits)
}



case class InitCmd(cpa : CoreParameterAggregate) extends Bundle{
  val ADDR  = Bits(cpa.ml.chipAddressWidth bits)
  val BA    = Bits(cpa.ml.bankWidth bits)
  val CASn  = Bool
  val CKE   = Bool
  val CSn   = Bool
  val RASn  = Bool
  val WEn   = Bool
}

case class SoftBus(cpa : CoreParameterAggregate) extends Bundle with IMasterSlave{
  val cmd = Flow(InitCmd(cpa))

  def driveFrom(mapper : BusSlaveFactory): Unit ={
    val valid = RegNext(mapper.isWriting(0x00))
    cmd.valid := valid
    mapper.drive(
      address = 0x04,
      0 -> cmd.CKE,
      1 -> cmd.CSn,
      2 -> cmd.RASn,
      3 -> cmd.CASn,
      4 -> cmd.WEn
    )
    mapper.drive(cmd.ADDR, 0x08)
    mapper.drive(cmd.BA, 0x0C)
  }

  override def asMaster(): Unit = {
    master(cmd)
  }
}



case class BmbToCorePort(ip : BmbParameter, cpp : CorePortParameter, cpa : CoreParameterAggregate) extends Component{
  val io = new Bundle{
    val input = slave(Bmb(ip))
    val output = master(CorePort(cpp, cpa))
  }

  case class Context() extends Bundle{
    val input = Bits(ip.contextWidth bits)
    val source = UInt(ip.sourceWidth bits)
  }

  val cmdContext = Context()
  cmdContext.input := io.input.cmd.context
  cmdContext.source := io.input.cmd.source


  io.output.cmd.arbitrationFrom(io.input.cmd)
  io.output.cmd.last := io.input.cmd.last
  io.output.cmd.write := io.input.cmd.isWrite
  io.output.cmd.address := io.input.cmd.address
  io.output.cmd.data := io.input.cmd.data
  io.output.cmd.mask := io.input.cmd.mask
  io.output.cmd.context := B(cmdContext)


  val rspContext = Context()
  rspContext.input := io.input.cmd.context
  rspContext.source := io.input.cmd.source

  io.input.rsp.arbitrationFrom(io.output.rsp)
  io.input.rsp.setSuccess()
  io.input.rsp.last := io.output.rsp.last
  io.input.rsp.data := io.output.rsp.data
  io.input.rsp.context := rspContext.input
  io.input.rsp.source := rspContext.source
}