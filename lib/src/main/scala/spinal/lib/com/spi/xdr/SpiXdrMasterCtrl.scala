package spinal.lib.com.spi.ddr


import spinal.core._
import spinal.lib._
import spinal.lib.bus.bmb.{Bmb, BmbParameter}
import spinal.lib.bus.misc.BusSlaveFactory
import spinal.lib.bus.simple.{PipelinedMemoryBus, PipelinedMemoryBusConfig}
import spinal.lib.com.spi.{SpiHalfDuplexMaster, SpiKind}
import spinal.lib.fsm.{State, StateMachine}
import spinal.lib.io.TriState

import scala.collection.mutable.ArrayBuffer

case class XdrOutput(rate : Int) extends Bundle with IMasterSlave{
  val write = Bits(rate bits)

  override def asMaster(): Unit = {
    out(write)
  }

  def toTriState(): TriState[Bool] ={
    assert(rate == 2)
    val io = TriState(Bool)
    val clk = ClockDomain.readClockWire
    val writeBuffer = RegNext(write)
    io.write := (clk ? writeBuffer(0))| writeBuffer(1)
    io
  }
}

case class XdrPin(rate : Int) extends Bundle with IMasterSlave{
  val writeEnable = Bool
  val read,write = Bits(rate bits)

  override def asMaster(): Unit = {
    out(write,writeEnable)
    in(read)
  }

  def toTriState(): TriState[Bool] ={
    assert(rate == 2)
    val io = TriState(Bool)
    val clk = ClockDomain.readClockWire
    io.writeEnable := writeEnable
    val writeBuffer = RegNext(write)
    io.write := (clk ? writeBuffer(0))| writeBuffer(1)
    def cd(edge : EdgeKind) = ClockDomain.current.copy(config = ClockDomain.current.config.copy(clockEdge = edge))
    read(0) := cd(RISING)(RegNext(io.read))
    read(1) := cd(FALLING)(RegNext(io.read))
    io
  }
}


case class SpiXdrParameter(dataWidth : Int,
                           ioRate : Int,
                           ssWidth : Int)

case class SpiXdrMaster(val p : SpiXdrParameter) extends Bundle with IMasterSlave{
  import p._

  val sclk = XdrOutput(p.ioRate)
  val data = Vec(XdrPin(p.ioRate), dataWidth)
  val ss   = if(ssWidth != 0) Bits(ssWidth bits) else null

  override def asMaster(): Unit = {
    master(sclk)
    if(ssWidth != 0) out(ss)
    data.foreach(master(_))
  }

  def toSpi(): SpiHalfDuplexMaster ={
    val spi = SpiHalfDuplexMaster(
      dataWidth = p.dataWidth,
      ssWidth = p.ssWidth,
      useSclk = true
    )

    p.ioRate match {
      case 1 => {
        spi.sclk := RegNext(sclk.write(0))
        if(ssWidth != 0) spi.ss := RegNext(ss)
        for(i <- 0 until p.dataWidth){
          spi.data.write(i) := RegNext(data(i).write(0))
          spi.data.writeEnable(i) := RegNext(data(i).writeEnable)
          data(i).read(0) := RegNext(spi.data.read(i))
        }
      }
    }

    spi
  }
}


object SpiXdrMasterCtrl {
  def apply(p : Parameters) = new TopLevel(p)



  def main(args: Array[String]): Unit = {
    SpinalVerilog(new TopLevel(Parameters(8,12,SpiXdrParameter(dataWidth = 4,ssWidth = 3, ioRate = 1)).addFullDuplex(0)))
  }




  case class WriteMapping(pin : Int, phase : Int, source : Int)
  case class ReadMapping(pin : Int, phase : Int, target : Int)
  case class Mod(id : Int, clkRate : Int, slowDdr : Boolean, dataWidth : Int, writeMapping : Seq[WriteMapping], readMapping : Seq[ReadMapping]){
    def bitrate = readMapping.length
  }
  case class Parameters(dataWidth : Int,
                        timerWidth : Int,
                        spi : SpiXdrParameter,
                        mods : ArrayBuffer[Mod] = ArrayBuffer()){

    val ModType = HardType(UInt(log2Up(mods.map(_.id).max + 1) bits))
    def ssGen = spi.ssWidth != 0
    def addFullDuplex(id : Int, rate : Int = 1, ddr : Boolean = false, dataWidth : Int = 8): this.type = addHalfDuplex(id,rate,ddr,1,dataWidth,1)
    def addHalfDuplex(id : Int, rate : Int, ddr : Boolean, spiWidth : Int, dataWidth : Int = 8, readPinOffset : Int = 0): this.type = {
      assert(isPow2(spi.ioRate))
      if(rate == 1) {
        val writeMapping = for (pinId <- (0 until spiWidth);
                                phaseId <- (0 until spi.ioRate)) yield WriteMapping(pinId, phaseId, pinId)

        val readMapping = for (pinId <- (0 until spiWidth)) yield ReadMapping(pinId + readPinOffset, 0, pinId)

        mods += Mod(id, rate, ddr, dataWidth, writeMapping, readMapping)
      } else {
        val pinRate = rate / (if(ddr) 1 else 2)
        val pinDuration =  spi.ioRate / pinRate
        val writeMapping = for (pinId <- (0 until spiWidth);
                                phaseId <- (0 until pinRate);
                                durationId <- (0 until pinDuration)) yield WriteMapping(pinId, phaseId*pinDuration + durationId, (pinRate - phaseId - 1)*spiWidth + pinId)

        val readMapping = for (pinId <- (0 until spiWidth);
                               phaseId <- (0 until pinRate)) yield ReadMapping(pinId + readPinOffset, (phaseId*pinDuration-1) & (spi.ioRate-1), (pinRate - phaseId - 1)*spiWidth + pinId)
        mods += Mod(id, rate, false, dataWidth, writeMapping, readMapping)
      }
      this
    }
  }

  case class Config(p: Parameters) extends Bundle {
    val kind = SpiKind()
    val sclkToogle = UInt(p.timerWidth bits)
    val mod = in(p.ModType())

    val ss = ifGen(p.ssGen) (new Bundle {
      val activeHigh = Bits(p.spi.ssWidth bits)
      val setup = UInt(p.timerWidth bits)
      val hold = UInt(p.timerWidth bits)
      val disable = UInt(p.timerWidth bits)
    })

  }

  case class Cmd(p: Parameters) extends Bundle{
    val kind = Bool
    val read, write = Bool
    val data = Bits(p.dataWidth bits)

    def isData = !kind
    def isSs = kind
    def getSsEnable = data.msb
    def getSsId = U(data(0, log2Up(p.spi.ssWidth) bits))
  }

  case class Rsp(p: Parameters) extends Bundle{
    val data = Bits(p.dataWidth bits)
  }

  case class MemoryMappingParameters(ctrl : Parameters,
                                     cmdFifoDepth : Int = 32,
                                     rspFifoDepth : Int = 32,
                                     cpolInit : Boolean = false,
                                     cphaInit : Boolean = false,
                                     modInit : Int = 0,
                                     sclkToogleInit : Int = 0,
                                     ssSetupInit : Int = 0,
                                     ssHoldInit : Int = 0,
                                     ssDisableInit : Int = 0,
                                     ssActiveHighInit : Int = 0,
                                     xipInstructionModInit: Int = 0,
                                     xipAddressModInit : Int = 0,
                                     xipDummyModInit : Int = 0,
                                     xipPayloadModInit : Int = 0,
                                     xipConfigWritable : Boolean = true,
                                     xipEnableInit : Boolean = false,
                                     xipInstructionEnableInit : Boolean = true,
                                     xipInstructionDataInit : Int = 0x0B,
                                     xipDummyCountInit : Int = 0,
                                     xipDummyDataInit : Int = 0xFF,
                                     xip : XipBusParameters = null)

  case class XipBusParameters(addressWidth : Int,
                              lengthWidth : Int)

  def getXipBmbCapabilities() = BmbParameter(
    addressWidth  = 24,
    dataWidth     = 8,
    lengthWidth   = Int.MaxValue,
    sourceWidth   = Int.MaxValue,
    contextWidth  = Int.MaxValue,
    canRead       = true,
    canWrite      = false,
    alignment     = BmbParameter.BurstAlignement.BYTE,
    maximumPendingTransactionPerId = Int.MaxValue
  )

  case class XipCmd(p : XipBusParameters) extends Bundle {
    val address = UInt(p.addressWidth bits)
    val length = UInt(p.lengthWidth bits)
  }


  case class XipBus(p : XipBusParameters) extends Bundle with IMasterSlave{
    val cmd = Stream(XipCmd(p))
    val rsp = Stream(Fragment(Bits(8 bits)))

    override def asMaster(): Unit = {
      master(cmd)
      slave(rsp)
    }

    def fromPipelinedMemoryBus() = {
      val accessBus = new PipelinedMemoryBus(PipelinedMemoryBusConfig(24,32))
      ???
//      cmd.valid <> (accessBus.cmd.valid && !accessBus.cmd.write)
//      cmd.ready <> accessBus.cmd.ready
//      cmd.payload <> accessBus.cmd.address
//
//      rsp.valid <> accessBus.rsp.valid
//      rsp.payload <> accessBus.rsp.data
      accessBus
    }

    def fromBmb(p : BmbParameter) = {
      val bmb = Bmb(p)
      cmd.valid := bmb.cmd.valid
      cmd.address := bmb.cmd.address
      cmd.length := bmb.cmd.length
      bmb.cmd.ready := cmd.ready

      bmb.rsp.valid := rsp.valid
      bmb.rsp.data := rsp.fragment
      bmb.rsp.last := rsp.last
      bmb.rsp.source  := RegNextWhen(bmb.cmd.source,  bmb.cmd.fire)
      bmb.rsp.context := RegNextWhen(bmb.cmd.context, bmb.cmd.fire)
      bmb.rsp.setSuccess()
      rsp.ready := bmb.rsp.ready

      bmb
    }
  }

  def driveFrom(toplevel : TopLevel, bus : BusSlaveFactory, baseAddress : Int = 0)(mapping : MemoryMappingParameters) = new Area {
    import mapping._
    import toplevel.io._
    def p = toplevel.p
    require(cmdFifoDepth >= 1)
    require(rspFifoDepth >= 1)

    require(cmdFifoDepth < 32.KiB)
    require(rspFifoDepth < 32.KiB)

    //CMD
    val cmdLogic = new Area {
      val streamUnbuffered = Stream(Cmd(p))
      streamUnbuffered.valid := bus.isWriting(address = baseAddress + 0)
      bus.nonStopWrite(streamUnbuffered.data, bitOffset = 0)
      bus.nonStopWrite(streamUnbuffered.write, bitOffset = 8)
      bus.nonStopWrite(streamUnbuffered.read, bitOffset = 9)
      bus.nonStopWrite(streamUnbuffered.kind, bitOffset = 11)


      bus.createAndDriveFlow(Cmd(p),address = baseAddress + 0).toStream
      val (stream, fifoAvailability) = streamUnbuffered.queueWithAvailability(cmdFifoDepth)
      cmd << stream
      bus.read(fifoAvailability, address = baseAddress + 4, 0)
    }

    //RSP
    val rspLogic = new Area {
      val (stream, fifoOccupancy) = rsp.queueWithOccupancy(rspFifoDepth)
      bus.readStreamNonBlocking(stream, address = baseAddress + 0, validBitOffset = 31, payloadBitOffset = 0, validInverted = true)
      bus.read(fifoOccupancy, address = baseAddress + 4, 16)
    }

    //Interrupts
    val interruptCtrl = new Area {
      val cmdIntEnable = bus.createReadAndWrite(Bool, address = baseAddress + 12, 0) init(False)
      val rspIntEnable  = bus.createReadAndWrite(Bool, address = baseAddress + 12, 1) init(False)
      val cmdInt = bus.read(cmdIntEnable & !cmdLogic.stream.valid, address = baseAddress + 12, 8)
      val rspInt = bus.read(rspIntEnable &  rspLogic.stream.valid, address = baseAddress + 12, 9)
      val interrupt = rspInt || cmdInt
    }

    //Configs
    bus.drive(config.kind, baseAddress + 8, bitOffset = 0)
    bus.drive(config.mod, baseAddress + 8, bitOffset = 4)
    bus.drive(config.sclkToogle, baseAddress + 0x20)
    if(p.ssGen) {
      bus.drive(config.ss.setup, baseAddress + 0x24)
      bus.drive(config.ss.hold, baseAddress + 0x28)
      bus.drive(config.ss.disable, baseAddress + 0x2C)
      bus.drive(config.ss.activeHigh, baseAddress + 0x30)
    }

    if(xipEnableInit){
      config.kind.cpol init(cpolInit)
      config.kind.cpha init(cphaInit)
      config.mod init(modInit)
      config.sclkToogle init(sclkToogleInit)
      config.ss.setup init(ssSetupInit)
      config.ss.hold init(ssHoldInit)
      config.ss.disable init(ssDisableInit)
      config.ss.activeHigh init(ssActiveHighInit)
    }

    val xip = ifGen(mapping.xip != null) (new Area{
      val xipBus = XipBus(mapping.xip)
      val enable = Reg(Bool)
      val instructionMod = Reg(p.ModType)
      val instructionEnable = Reg(Bool)
      val instructionData = Reg(Bits(8 bits))
      val addressMod = Reg(p.ModType)
      val dummyCount = Reg(UInt(4 bits))
      val dummyData = Reg(Bits(8 bits))
      val dummyMod = Reg(p.ModType)
      val payloadMod = Reg(p.ModType)

      if(xipEnableInit){
        enable init(True)
        instructionMod init(xipInstructionModInit)
        addressMod init(xipAddressModInit)
        dummyMod init(xipDummyModInit)
        payloadMod init(xipPayloadModInit)
        instructionEnable init(Bool(xipInstructionEnableInit))
        instructionData init(xipInstructionDataInit)
        dummyCount init(xipDummyCountInit)
        dummyData init(xipDummyDataInit)
      } else {
        enable init(True)
      }

      bus.write(enable, baseAddress + 0x40)
      if(xipConfigWritable) {
        bus.write(instructionData, baseAddress + 0x44, bitOffset = 0)
        bus.write(instructionEnable, baseAddress + 0x44, bitOffset = 8)
        bus.write(dummyData, baseAddress + 0x44, bitOffset = 16)
        bus.write(dummyCount, baseAddress + 0x44, bitOffset = 24)

        bus.write(instructionMod, baseAddress + 0x48, bitOffset = 0)
        bus.write(addressMod, baseAddress + 0x48, bitOffset = 8)
        bus.write(dummyMod, baseAddress + 0x48, bitOffset = 16)
        bus.write(payloadMod, baseAddress + 0x48, bitOffset = 24)
      }

      val fsm = new StateMachine{
        val IDLE, INSTRUCTION, ADDRESS, DUMMY, PAYLOAD, STOP = State()
        setEntry(IDLE)

        val cmdLength = Reg(UInt(mapping.xip.lengthWidth bits))

        val rspCounter = Reg(UInt(mapping.xip.lengthWidth bits))
        val rspCounterMatch = rspCounter === cmdLength
        require(mapping.rspFifoDepth >= 4, "rsp fifo required for XIP operation")

        xipBus.rsp.valid := False
        xipBus.rsp.fragment := rspLogic.stream.data
        xipBus.rsp.last := rspCounterMatch

        val cmdHalt = False
        rspLogic.stream.ready clearWhen(cmdHalt)

        IDLE.whenIsInactive{
          xipBus.rsp.valid setWhen(rspLogic.stream.valid)
          rspLogic.stream.ready setWhen(xipBus.rsp.ready)
          cmdHalt setWhen(xipBus.rsp.isStall)
          when(rspLogic.stream.fire){
            rspCounter := rspCounter + 1
          }
        }


        val xipToCtrlCmd = cloneOf(cmd)
        xipToCtrlCmd.valid := False
        xipToCtrlCmd.payload.assignDontCare()

        val xipToCtrlCmdBuffer = xipToCtrlCmd.haltWhen(cmdHalt).stage
        when(xipToCtrlCmdBuffer.valid){
          cmd.valid := True
          cmd.payload := xipToCtrlCmdBuffer.payload
        }
        xipToCtrlCmdBuffer.ready := cmd.ready

        IDLE.whenIsActive{
          cmdLength := xipBus.cmd.length
          rspCounter := 0
          when(xipBus.cmd.valid){
            xipToCtrlCmd.valid := True
            xipToCtrlCmd.kind := True
            xipToCtrlCmd.data := 1 << xipToCtrlCmd.data.high
            when(xipToCtrlCmd.ready) {
              when(instructionEnable) {
                goto(INSTRUCTION)
              } otherwise {
                goto(ADDRESS)
              }
            }
          }
        }

        INSTRUCTION.whenIsActive{
          xipToCtrlCmd.valid := True
          xipToCtrlCmd.kind := False
          xipToCtrlCmd.write := True
          xipToCtrlCmd.read := False
          xipToCtrlCmd.data := instructionData
          config.mod := instructionMod
          when(xipToCtrlCmd.ready) {
            goto(ADDRESS)
          }
        }

        xipBus.cmd.ready := False
        val counter = Reg(UInt(Math.max(4, mapping.xip.lengthWidth) bits)) init(0)
        ADDRESS.onEntry(counter := 0)
        ADDRESS.whenIsActive{
          xipToCtrlCmd.valid := True
          xipToCtrlCmd.kind := False
          xipToCtrlCmd.write := True
          xipToCtrlCmd.read := False
          xipToCtrlCmd.data := xipBus.cmd.address.subdivideIn(8 bits).reverse(counter(1 downto 0)).asBits
          config.mod := addressMod
          when(xipToCtrlCmd.ready) {
            counter := counter + 1
            when(counter === 2) {
              xipBus.cmd.ready := True
              goto(DUMMY)
            }
          }
        }


        DUMMY.onEntry(counter := 0)
        DUMMY.whenIsActive{
          xipToCtrlCmd.valid := True
          xipToCtrlCmd.kind := False
          xipToCtrlCmd.write := True
          xipToCtrlCmd.read := False
          xipToCtrlCmd.data := dummyData
          config.mod := dummyMod
          when(xipToCtrlCmd.ready) {
            counter := counter + 1
            when(counter === dummyCount) {
              goto(PAYLOAD)
            }
          }
        }

        PAYLOAD.onEntry(counter := 0)
        PAYLOAD.whenIsActive {
          config.mod := payloadMod
          xipToCtrlCmd.kind := False
          xipToCtrlCmd.write := False
          xipToCtrlCmd.read := True
          xipToCtrlCmd.valid := True
          when(xipToCtrlCmd.ready) {
            counter := counter + 1
            when(counter === cmdLength) {
              goto(STOP)
            }
          }
        }

        val lastFired = Reg(Bool) setWhen(xipBus.rsp.lastFire)
        STOP.onEntry(lastFired := False)
        STOP.whenIsActive{
          xipToCtrlCmd.kind := True
          xipToCtrlCmd.data := 0
          when(lastFired){
            xipToCtrlCmd.valid := True
            when(xipToCtrlCmd.ready) {
              goto(IDLE)
            }
          }
        }
      }
    })
  }


  class TopLevel(val p: Parameters) extends Component {
    setDefinitionName("SpiXdrMasterCtrl")

    val io = new Bundle {
      val config = in(Config(p))
      val cmd = slave(Stream(Cmd(p)))
      val rsp = master(Flow(Rsp(p)))
      val spi = master(SpiXdrMaster(p.spi))
    }

    val timer = new Area{
      val counter = Reg(UInt(p.timerWidth bits))
      val reset = False
      val ss = ifGen(p.ssGen) (new Area{
        val setupHit    = counter === io.config.ss.setup
        val holdHit     = counter === io.config.ss.hold
        val disableHit  = counter === io.config.ss.disable
      })
      val sclkToogleHit = counter === io.config.sclkToogle

      counter := (counter + 1).resized
      when(reset){
        counter := 0
      }
    }

    val widths = p.mods.map(m => m.bitrate).distinct.sorted
    val widthMax = widths.max


    val fsm = new Area {
      val state = RegInit(False)
      val counter = Reg(UInt(log2Up(p.dataWidth) bits)) init(0)
      val bitrateMax = p.mods.map(_.bitrate).max
      val counterPlus = counter + io.config.mod.muxListDc(p.mods.map(m => m.id -> U(m.bitrate, log2Up(bitrateMax + 1) bits))).resized
      val fastRate = io.config.mod.muxListDc(p.mods.map(m => m.id -> Bool(m.clkRate != 1)))
      val isDdr = io.config.mod.muxListDc(p.mods.map(m => m.id -> Bool(m.slowDdr)))
      val readFill, readDone = False
      val ss = p.ssGen generate (Reg(Bits(p.spi.ssWidth bits)) init(0))
      p.ssGen generate (io.spi.ss := ~(ss ^ io.config.ss.activeHigh))

      io.cmd.ready := False
      when(io.cmd.valid) {
        when(io.cmd.isData) {
          timer.reset := timer.sclkToogleHit
          when(timer.sclkToogleHit){
            state := !state
          }
          when((timer.sclkToogleHit && (state || isDdr)) || fastRate) {
            counter := counterPlus
            readFill := True
            when(counterPlus === 0){
              io.cmd.ready := True
              readDone := io.cmd.read
              state := False
            }
          }
        } otherwise {
          if (p.ssGen) {
            when(io.cmd.getSsEnable) {
              ss(io.cmd.getSsId) := True
              when(timer.ss.setupHit) {
                io.cmd.ready := True
              }
            } otherwise {
              when(!state) {
                when(timer.ss.holdHit) {
                  state := True
                  timer.reset := True
                }
              } otherwise {
                ss(io.cmd.getSsId) := False
                when(timer.ss.disableHit) {
                  io.cmd.ready := True
                }
              }
            }
          }
        }
      }

      //Idle states
      when(!io.cmd.valid || io.cmd.ready){
        state := False
        counter := 0
        timer.reset := True
      }
    }


    val maxBitRate = p.mods.map(m => m.bitrate).max
    val outputPhy = new Area {
      val rates = p.mods.map(m => m.id -> log2Up(m.clkRate))

      //Generate SCLK
      val sclkWrite = B(0, p.spi.ioRate bits)
      io.spi.sclk.write := sclkWrite ^ B(sclkWrite.range -> io.config.kind.cpol)
      when(io.cmd.valid && io.cmd.isData){
        switch(io.config.mod) {
          for (m <- p.mods) {
            is(m.id){
              m.clkRate match {
                case 1 => sclkWrite := (default -> (fsm.state ^ io.config.kind.cpha))
                case _ => for(bitId <- 0 until p.spi.ioRate){
                  sclkWrite(bitId) := (if((bitId * m.clkRate / p.spi.ioRate) % 2 == 0) io.config.kind.cpha else !io.config.kind.cpha)
                }
              }
            }
          }
        }
      }


      //Get raw data to put on MOSI
      val dataWrite = Bits(maxBitRate bits)
      val widthSel = io.config.mod.muxListDc( p.mods.map(m => m.id -> U(widths.indexOf(m.bitrate), log2Up(widthMax + 1) bits)))
      dataWrite.assignDontCare()
      switch(widthSel){
        for((width, widthId) <- widths.zipWithIndex){
          is(widthId){
            dataWrite(0, width bits) := io.cmd.data.subdivideIn(width bits).reverse(fsm.counter >> log2Up(width))
          }
        }
      }


      //Set MOSI signals
      io.spi.data.foreach(_.writeEnable := False)
      io.spi.data.foreach(_.write.assignDontCare())
      switch(io.config.mod){
        for(mod <- p.mods){
          is(mod.id) {
            when(io.cmd.valid && io.cmd.write){
              mod.writeMapping.map(_.pin).distinct.foreach(i => io.spi.data(i).writeEnable := True)
            }
            for (mapping <- mod.writeMapping) {
              io.spi.data(mapping.pin).write(mapping.phase) := dataWrite(mapping.source)
            }
          }
        }
      }
    }


    val inputPhy = new Area{
      def sync[T <: Data](that : T, init : T = null) = Delay(that,2,init=init)
      val mod = sync(io.config.mod)
      val readFill = sync(fsm.readFill, False)
      val readDone = sync(fsm.readDone, False)
      val buffer = Reg(Bits(p.dataWidth - p.mods.map(_.bitrate).min bits))
      val bufferNext = Bits(p.dataWidth bits).assignDontCare().allowOverride
      val widthSel = mod.muxListDc(p.mods.map(m => m.id -> U(widths.indexOf(m.bitrate), log2Up(widthMax + 1) bits)))
      val dataWrite, dataRead = Bits(maxBitRate bits)
      val dataReadBuffer = RegNextWhen(Cat(io.spi.data.map(_.read(0))), !sync(fsm.state))

      dataRead.assignDontCare()

      switch(mod){
        for(mod <- p.mods){
          is(mod.id) {
            for(mapping <- mod.readMapping) {
              if(mod.clkRate != 1 || mod.slowDdr) {
                dataRead(mapping.target) := io.spi.data(mapping.pin).read(mapping.phase)
              }else{
                assert(mapping.phase == 0)
                dataRead(mapping.target) := Cat(io.spi.data.map(_.read(0)))(mapping.pin)//dataReadBuffer(mapping.pin)
              }
            }
          }
        }
      }


      switch(widthSel) {
        for ((width,widthId) <- widths.zipWithIndex) {
          is(widthId) {
            bufferNext := (buffer ## dataRead(0, width bits)).resized
            when(readFill) { buffer := bufferNext.resized }
          }
        }
      }

      io.rsp.valid := readDone
      io.rsp.data := bufferNext
    }
  }
}

