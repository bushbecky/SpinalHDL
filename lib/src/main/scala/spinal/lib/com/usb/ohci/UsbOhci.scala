package spinal.lib.com.usb.ohci

import spinal.core._
import spinal.lib._
import spinal.lib.bus.bmb._
import spinal.lib.com.eth.{Crc, CrcKind}
import spinal.lib.com.usb.phy.UsbHubLsFs
import spinal.lib.fsm._

import scala.collection.mutable.ArrayBuffer

case class OhciPortParameter(removable : Boolean = true, powerControlMask : Boolean = true)

case class UsbOhciParameter(noPowerSwitching : Boolean,
                            powerSwitchingMode : Boolean,
                            noOverCurrentProtection : Boolean,
//                            overCurrentProtectionMode : Boolean,
                            powerOnToPowerGoodTime : Int,
                            fsRatio : Int,
                            dataWidth : Int,
                            portsConfig : Seq[OhciPortParameter],
                            dmaLengthWidth : Int = 6){
  assert(dmaLengthWidth >= 5 && dmaLengthWidth <= 12)
  def dmaLength = 1 << dmaLengthWidth
  val portCount = portsConfig.size
}

object UsbOhci{
  def dmaParameter(p : UsbOhciParameter) = BmbParameter(
    addressWidth = 32,
    dataWidth = p.dataWidth,
    sourceWidth = 0,
    contextWidth = 0,
    lengthWidth = 6,
    alignment = BmbParameter.BurstAlignement.BYTE
  )
}

object UsbPid{
  val OUT = Integer.parseInt("0001",2)
  val IN = Integer.parseInt("1001",2)
  val SOF = Integer.parseInt("0101",2)
  val SETUP = Integer.parseInt("1101",2)
  val DATA0 = Integer.parseInt("0011",2)
  val DATA1 = Integer.parseInt("1011",2)
  val DATA2 = Integer.parseInt("0111",2)
  val MDATA = Integer.parseInt("1111",2)
  val ACK = Integer.parseInt("0010",2)
  val NAK = Integer.parseInt("1010",2)
  val STALL = Integer.parseInt("1110",2)
  val NYET = Integer.parseInt("0110",2)
  val PRE = Integer.parseInt("1100",2)
  val ERR = Integer.parseInt("1100",2)
  val SPLIT = Integer.parseInt("1000",2)
  val PING = Integer.parseInt("0100",2)
}

case class UsbOhci(p : UsbOhciParameter, ctrlParameter : BmbParameter) extends Component{
  val io = new Bundle {
    val ctrl = slave(Bmb(ctrlParameter))
    val phy = master(UsbHubLsFs.Ctrl(p.portCount))
    val dma = master(Bmb(UsbOhci.dmaParameter(p)))
  }
  io.phy.lowSpeed := False
  
  val unscheduleAll = Event
  unscheduleAll.valid := False
  unscheduleAll.ready := True

  val ioDma = Bmb(UsbOhci.dmaParameter(p))
  io.dma.cmd << ioDma.cmd.haltWhen(unscheduleAll.valid && io.dma.cmd.first)
  io.dma.rsp >> ioDma.rsp


  ioDma.cmd.valid := False
  ioDma.cmd.payload.assignDontCare()
  ioDma.cmd.data.removeAssignments() := 0
  ioDma.cmd.mask.removeAssignments() := 0
  ioDma.rsp.ready := True

  val dmaRspMux = new Area{
    val vec = ioDma.rsp.data.subdivideIn(32 bits)
    val sel = UInt(log2Up(vec.length) bits)
    val data = vec(sel)
  }

  //Follow the DMA read response to setup internals registers via the usage of the load function
  val dmaReadCtx = new Area{
    val wordPerBeat = p.dataWidth/32
    val counterStates = (1 << p.dmaLengthWidth)/(p.dataWidth/8)
    val counter = Reg(UInt(log2Up(counterStates) bits)) init(0)
    when(ioDma.rsp.fire){
      counter := counter + 1
      when(ioDma.rsp.last){
        counter := 0
      }
    }
    def load[T <: Data](reg : T, word : Int, bit : Int): Unit = {
      when(ioDma.rsp.valid && counter === word/wordPerBeat){
        reg.assignFromBits(dmaRspMux.vec(word % wordPerBeat)(bit, widthOf(reg) bits))
      }
    }
  }

  //Follow the DMA write cmd to set the appropriated cmd.data values following the save function calls
  val dmaWriteCtx = new Area{
    val wordPerBeat = p.dataWidth/32
    val counterStates = (1 << p.dmaLengthWidth)/(p.dataWidth/8)
    val counter = Reg(UInt(log2Up(counterStates) bits)) init(0)
    when(ioDma.cmd.fire){
      counter := counter + 1
      when(ioDma.cmd.last){
        counter := 0
      }
    }
    def save[T <: Data](value : T, word : Int, bit : Int): Unit = {
      when(counter === word/wordPerBeat){
        val offset = (word % wordPerBeat) * 32 + bit
        ioDma.cmd.mask(offset/8, (widthOf(value)+7)/8 bits).setAll()
        ioDma.cmd.data(offset, widthOf(value) bits) := value.asBits
      }
    }
  }

  // Track the number of inflight memory requests, used to be sure everything is done before continuing
  val ramBurstCapacity = 4
  val dmaCtx = new Area{
    val pendingCounter = Reg(UInt(log2Up(ramBurstCapacity+1) bits)) init(0)
    pendingCounter := pendingCounter + U(ioDma.cmd.lastFire) - U(ioDma.rsp.lastFire)

    val pendingFull = pendingCounter === ramBurstCapacity
    val pendingEmpty = pendingCounter === 0

    unscheduleAll.ready clearWhen(!pendingEmpty)
  }

  // Used as buffer for USB data <> DMA transfers
  val dataBuffer = new Area{
    val ram = Mem(Bits(p.dataWidth bits), ramBurstCapacity * p.dmaLength*8/p.dmaLengthWidth)

    def counterGen() = new Area{
      val value = Reg(ram.addressType)
      val clear, increment = False
      when(increment){
        value := value + 1
      }
      when(clear){
        value := 0
      }
    }

    val write = new Area{
      val cmd = ram.writePort
      val counter = counterGen()
      cmd.valid := False
      cmd.address := counter.value
      cmd.data.assignDontCare()
    }
    val read = new Area{
      val cmd = Stream(ram.addressType)
      val cmdCtx = Bits(1 + log2Up(p.dataWidth/8) bits)
      val rsp = ram.streamReadSync(cmd, cmdCtx)

      val counter = counterGen()
      cmd.valid := False
      cmd.payload := counter.value
      cmdCtx.assignDontCare()
      rsp.ready := False
    }
  }

  io.phy.tx.valid := False
  io.phy.tx.fragment.assignDontCare()
  io.phy.tx.last.assignDontCare()

  val ctrlHalt = False
  val ctrl = BmbSlaveFactory(io.ctrl)
  when(ctrlHalt) { ctrl.writeHalt() } //Ensure no race conditions between io.ctrl and HC

  val doUnschedule = RegInit(False) clearWhen(unscheduleAll.ready)
  val doSoftReset = RegInit(False) clearWhen(!doUnschedule)
  unscheduleAll.valid setWhen(doUnschedule)
  
  val softInitTasks = ArrayBuffer[() => Unit]()
  implicit class OhciDataPimper[T <: Data](self : T){
    def softInit(value : T) : T = {
      softInitTasks += (() => self := value)
      self
    }
  }
  implicit class OhciDataPimperBool(self : Bool) extends OhciDataPimper(self){
    def softInit(value : Boolean) : Bool = {
      softInitTasks += (() => self := Bool(value))
      self
    }
  }
  afterElaboration{
    when(doSoftReset || RegNext(False).init(True)){
      softInitTasks.foreach(_())
    }
  }





  object MainState extends SpinalEnum{
    val RESET, RESUME, OPERATIONAL, SUSPEND = newElement()
  }


  val reg = new Area {
    val hcRevision = new Area {
      val REV = ctrl.read(B(0x10), 0x00)
    }

    val hcControl = new Area {
      val CBSR = ctrl.createReadAndWrite(UInt(2 bits), 0x04, 0) softInit (0)
      val PLE = ctrl.createReadAndWrite(Bool(), 0x04, 2) softInit (False)
      val IE = ctrl.createReadAndWrite(Bool(), 0x04, 3) softInit (False)
      val CLE = ctrl.createReadAndWrite(Bool(), 0x04, 4) softInit (False)
      val BLE = ctrl.createReadAndWrite(Bool(), 0x04, 5) softInit (False)
      val HCFS = ctrl.read(MainState(), 0x04, 6)
      val IR =  ctrl.createReadAndWrite(Bool(), 0x04, 8) init(False)
      val RWC = ctrl.createReadAndWrite(Bool(), 0x04, 9) init(False)
      val RWE = ctrl.createReadAndWrite(Bool(), 0x04, 10) softInit (False)


      val HCFSWrite = ctrl.createAndDriveFlow(MainState(), 0x04, 6)
    }

    val hcCommandStatus = new Area {
      val startSoftReset = ctrl.setOnSet(False, 0x08, 0)
      val HCR = ctrl.read(doSoftReset, 0x08, 0)
      val CLF = ctrl.createReadAndSetOnSet(doSoftReset, 0x08, 1) softInit (False)
      val BLF = ctrl.createReadAndSetOnSet(Bool(), 0x08, 2) softInit (False)
      val SOC = ctrl.createReadOnly(UInt(2 bits), 0x08, 16) softInit (0)
    }

    def createInterrupt(id: Int) = new Area {
      val status = ctrl.createReadAndClearOnSet(Bool(), 0x0C, id) softInit (False)
      val enable = Reg(Bool) softInit(False)
      ctrl.readAndSetOnSet(enable, 0x10, id)
      ctrl.readAndClearOnSet(enable, 0x14, id)
    }

    val hcInterrupt = new Area {
      val MIE = Reg(Bool) softInit(False)
      ctrl.readAndSetOnSet(MIE, 0x10, 31)
      ctrl.readAndClearOnSet(MIE, 0x14, 31)
      val SO = createInterrupt(0)
      val WDH = createInterrupt(1)
      val SF = createInterrupt(2)
      val RD = createInterrupt(3)
      val UE = createInterrupt(4)
      val FNO = createInterrupt(5)
      val RHSC = createInterrupt(6)
      val OC = createInterrupt(30)
    }

    def mapAddress(zeroWidth: Int, mapping: Int, driverWrite: Boolean) = new Area {
      val address = UInt(32 bits)
      val reg = Reg(UInt(32 - zeroWidth bits)) softInit (0)
      ctrl.read(reg, mapping, zeroWidth)
      if (driverWrite) ctrl.write(reg, mapping, zeroWidth)
      address := reg @@ U(0, zeroWidth bits)

      def load(that: Bits): Unit = {
        reg := that(31 downto zeroWidth).asUInt
      }
    }

    val hcHCCA = new Area {
      val HCCA = mapAddress(8, 0x18, true)
    }

    val hcPeriodCurrentED = new Area {
      val PCED = mapAddress(4, 0x1C, false)
      val isZero = PCED.reg === 0
    }

    val hcControlHeadED = new Area {
      val CHED = mapAddress(4, 0x20, true)
    }

    val hcControlCurrentED = new Area {
      val CCED = mapAddress(4, 0x24, true)
      val isZero = CCED.reg === 0
    }

    val hcBulkHeadED = new Area {
      val BHED = mapAddress(4, 0x28, true)
    }

    val hcBulkCurrentED = new Area {
      val BCED = mapAddress(4, 0x2C, true)
      val isZero = BCED.reg === 0
    }

    val hcDoneHead = new Area {
      val DH = mapAddress(4, 0x30, true)
    }

    val hcFmInterval = new Area {
      val FI = ctrl.createReadAndWrite(UInt(14 bits), 0x34, 0) softInit (11999)
      val FSMPS = ctrl.createReadAndWrite(UInt(15 bits), 0x34, 16) //softInit(???)
      val FIT = ctrl.createReadAndWrite(Bool(), 0x34, 31) softInit (False)
    }

    val hcFmRemaining = new Area {
      val FR = ctrl.createReadOnly(UInt(14 bits), 0x38, 0) softInit (0)
      val FRT = ctrl.createReadOnly(Bool(), 0x38, 31) softInit (False)
    }

    val hcFmNumber = new Area {
      val FN = ctrl.createReadOnly(UInt(16 bits), 0x3C, 0) softInit (0)
    }

    val hcPeriodicStart = new Area {
      val PS = ctrl.createReadAndWrite(UInt(14 bits), 0x40, 0) init (0)
    }

    val hcLSThreshold = new Area {
      val LST = ctrl.createReadAndWrite(UInt(12 bits), 0x44, 0) softInit (0x0628)
      val hit = hcFmRemaining.FR < LST
    }

    val hcRhDescriptorA = new Area {
      val NDP = ctrl.read(U(p.portCount, 8 bits), 0x48, 0)
      val PSM = ctrl.createReadAndWrite(Bool(), 0x48, 8) softInit (p.powerSwitchingMode)
      val NPS = ctrl.createReadAndWrite(Bool(), 0x48, 9) softInit (p.noPowerSwitching)
      val OCPM = ctrl.createReadAndWrite(Bool(), 0x48, 11) softInit (p.powerSwitchingMode)
      val NOCP = ctrl.createReadAndWrite(Bool(), 0x48, 12) softInit (p.noOverCurrentProtection)
      val POTPGT = ctrl.createReadAndWrite(UInt(8 bits), 0x48, 24) softInit (p.powerOnToPowerGoodTime)
    }

    val hcRhDescriptorB = new Area {
      val DR = ctrl.createReadAndWrite(Bits(p.portCount bits), 0x4C, 1) softInit (Cat(p.portsConfig.map(!_.removable).map(Bool(_))))
      val PPCM = ctrl.createReadAndWrite(Bits(p.portCount bits), 0x4C, 17) softInit (Cat(p.portsConfig.map(_.powerControlMask).map(Bool(_))))
    }

    val hcRhStatus = new Area {
      val OCI = ctrl.read(io.phy.overcurrent, 0x50, 1)
      val DRWE = ctrl.createReadOnly(Bool, 0x50, 15) softInit (False)
      val CCIC = ctrl.createReadAndClearOnSet(Bool, 0x50, 17) softInit (False) setWhen (OCI.edge(False))

      val clearGlobalPower = ctrl.setOnSet(False, 0x50, 0)
      val setRemoteWakeupEnable = ctrl.setOnSet(False, 0x50, 15)
      val setGlobalPower = ctrl.setOnSet(False, 0x50, 16)
      val clearRemoteWakeupEnable = ctrl.setOnSet(False, 0x50, 31)

      DRWE setWhen (setRemoteWakeupEnable) clearWhen (clearRemoteWakeupEnable)
    }

    val hcRhPortStatus = for (portId <- 0 to p.portCount - 1) yield new Area {

      import hcRhDescriptorB._

      val id = portId + 1
      val port = io.phy.ports(portId)
      val address = 0x54 + portId * 4

      def change(bitId: Int) = new Area {
        val set = Bool()
        val clear = False
        val reg = ctrl.createReadOnly(Bool(), address, bitId) softInit (False) clearWhen (clear) setWhen (set)
        ctrl.setOnSet(clear, address, bitId)
      }

      val clearPortEnable = ctrl.setOnSet(False, address, 0)
      val setPortEnable = ctrl.setOnSet(False, address, 1)
      val setPortSuspend = ctrl.setOnSet(False, address, 2)
      val clearSuspendStatus = ctrl.setOnSet(False, address, 3)
      val setPortReset = ctrl.setOnSet(False, address, 4)
      val setPortPower = ctrl.setOnSet(False, address, 8)
      val clearPortPower = ctrl.setOnSet(False, address, 9)

      val resume, reset, suspend = RegInit(False)

      val connected = RegInit(False) setWhen (port.connect) clearWhen (port.disconnect)
      val PSS = ctrl.createReadOnly(Bool(), address, 2) init (False)
      val PPS = ctrl.createReadOnly(Bool(), address, 8) init (False) //TODO init port registers on HC reset ?
      val CCS = ctrl.read((connected || DR(portId)) && PPS, address, 0) //MAYBUG DR => always one ???
      val PES = ctrl.createReadOnly(Bool(), address, 1) init (False)
      val POCI = ctrl.read(port.overcurrent, address, 3)
      val PRS = ctrl.read(reset, address, 4)
      val LSDA = ctrl.read(port.lowSpeed, address, 9)

      val CSC = change(16)
      val PESC = change(17)
      val PSSC = change(18)
      val OCIC = change(19)
      val PRSC = change(20)

      PES clearWhen (clearPortEnable || PESC.set || !PPS) setWhen (PRSC.set || PSSC.set) setWhen (setPortEnable && CCS)
      PSS clearWhen (PSSC.set || PRSC.set || !PPS || hcControl.HCFS === MainState.RESUME) setWhen (setPortSuspend && CCS)
      suspend setWhen (setPortSuspend && CCS)
      resume setWhen (clearSuspendStatus && PSS)
      reset setWhen (setPortReset && CCS)

      when(hcRhDescriptorA.NPS) {
        PPS := True
      } otherwise {
        when(hcRhDescriptorA.PSM) {
          when(hcRhDescriptorB.PPCM(portId)) {
            PPS clearWhen (clearPortPower) setWhen (setPortPower)
          } otherwise {
            PPS clearWhen (hcRhStatus.clearGlobalPower) setWhen (hcRhStatus.setGlobalPower)
          }
        } otherwise {
          PPS clearWhen (hcRhStatus.clearGlobalPower) setWhen (hcRhStatus.setGlobalPower)
        }
      }

      if (!p.noOverCurrentProtection) {
        if (p.powerSwitchingMode) PPS clearWhen (port.overcurrent)
        if (!p.powerSwitchingMode) PPS clearWhen (io.phy.overcurrent)
      }

      CSC.set := CCS.edge(False) || (setPortEnable && !CCS) || (setPortSuspend && !CCS) || (setPortReset && !CCS)
      PESC.set := port.overcurrent
      PSSC.set := port.suspend.fire || port.remoteResume
      OCIC.set := POCI
      PRSC.set := port.reset.fire

      PSSC.clear setWhen (PRSC.set)

      //      port.enable := PES
      port.disable.valid := clearPortEnable //TODO
      port.removable := DR(portId)
      port.power := PPS //PPCM(portId)
      port.resume.valid := resume
      resume clearWhen (port.resume.fire)
      port.reset.valid := reset
      reset clearWhen (port.reset.fire)
      port.suspend.valid := suspend
      suspend.clearWhen(port.suspend.fire)


    }
  }

  val bitTimer = new Area {
    val reload = False
    val counter = CounterFreeRun(p.fsRatio)
    val tick = counter.willOverflow === True
    when(reload) {
      counter.clear()
    }
  }

  // Track the progress of the current frame (1 ms)
  val frame = new Area {
    val run = False
    val reload = False
    val overflow = reg.hcFmRemaining.FR === 0
    val tick = False
    val section1 = reg.hcFmRemaining.FR > reg.hcPeriodicStart.PS
    val limitCounter = Reg(UInt(15 bits))
    val limitHit = limitCounter === 0
    val decrementTimer = Reg(UInt(3 bits))

    val decrementTimerOverflow = decrementTimer === 6
    decrementTimer := decrementTimer + 1
    when(decrementTimerOverflow) {
      decrementTimer := 0
    }

    when(run && bitTimer.tick) {
      reg.hcFmRemaining.FR := reg.hcFmRemaining.FR - 1
      when(!limitHit && !decrementTimerOverflow) {
        limitCounter := limitCounter - 1
      }
      when(overflow) {
        tick := True
        reload := True
      }
    }
    when(reload) {
      reg.hcFmRemaining.FR := reg.hcFmInterval.FI
      reg.hcFmRemaining.FRT := reg.hcFmInterval.FIT
      reg.hcFmNumber.FN := reg.hcFmNumber.FN + 1
      limitCounter := reg.hcFmInterval.FSMPS
      decrementTimer := 0
    }
  }

  // FSM to emit tocken
  val token = new StateMachineSlave {
    val pid = Bits(4 bits).assignDontCare()
    val data = Bits(11 bits).assignDontCare()

    val INIT, PID, B1, B2 = new State
    setEntry(INIT)


    val crc5 = Crc(CrcKind.usb.crc5, 11)
    crc5.io.flush := False
    onStart {
      crc5.io.flush := True
    }

    crc5.io.input.valid := False
    crc5.io.input.payload := data
    INIT.whenIsActive {
      crc5.io.input.valid := True
      goto(PID)
    }

    PID.whenIsActive {
      io.phy.tx.valid := True
      io.phy.tx.last := False
      io.phy.tx.fragment := ~pid ## pid
      when(io.phy.tx.ready) {
        goto(B1)
      }
    }

    B1.whenIsActive {
      io.phy.tx.valid := True
      io.phy.tx.last := False
      io.phy.tx.fragment := data(0, 8 bits)
      when(io.phy.tx.ready) {
        goto(B2)
      }
    }

    B2.whenIsActive {
      io.phy.tx.valid := True
      io.phy.tx.fragment := crc5.io.result ## data(8, 3 bits)
      io.phy.tx.last := True
      when(io.phy.tx.ready) {
        exitFsm()
      }
    }

    always{
      when(unscheduleAll.fire){ killFsm() }
    }
  }

  // FSM to emit a data packet (from dma)
  val dataTx = new StateMachineSlave {
    val pid = Bits(4 bits).assignDontCare()
    val data = Stream(Fragment(Bits(8 bits)))

    val PID, DATA, CRC_0, CRC_1 = new State
    setEntry(PID)

    val crc16 = Crc(CrcKind.usb.crc16, 8)


    data.valid := False
    data.payload.assignDontCare()
    data.ready := False

    crc16.io.input.valid := data.fire
    crc16.io.input.payload := data.fragment
    crc16.io.flush := False


    PID.whenIsActive {
      crc16.io.flush := True
      io.phy.tx.valid := True
      io.phy.tx.last := False
      io.phy.tx.fragment := ~pid ## pid
      when(io.phy.tx.ready) {
        when(data.valid) {
          goto(DATA)
        } otherwise {
          goto(CRC_0)
        }
      }
    }

    DATA.whenIsActive {
      io.phy.tx.valid := True
      io.phy.tx.last := False
      io.phy.tx.fragment := data.fragment
      when(io.phy.tx.ready) {
        data.ready := True
        when(data.last) {
          goto(CRC_0)
        }
      }
    }

    CRC_0.whenIsActive {
      io.phy.tx.valid := True
      io.phy.tx.fragment := crc16.io.result(7 downto 0)
      io.phy.tx.last := False
      when(io.phy.tx.ready) {
        goto(CRC_1)
      }
    }

    CRC_1.whenIsActive {
      io.phy.tx.valid := True
      io.phy.tx.fragment := crc16.io.result(15 downto 8)
      io.phy.tx.last := True
      when(io.phy.tx.ready) {
        exitFsm()
      }
    }

    always{
      when(unscheduleAll.fire){ killFsm() }
    }
  }


  val sof = new StateMachineSlave {
    val FRAME_TX, FRAME_NUMBER_CMD, FRAME_NUMBER_RSP = new State
    setEntry(FRAME_TX)

    val doInterruptDelay = Reg(Bool)
    val unmaskedInterruptDelay = Reg(Bool)

    onStart {
      token.startFsm()
    }
    FRAME_TX.whenIsActive {
      token.data := reg.hcFmNumber.FN.asBits.resized
      token.pid := 5
      doInterruptDelay := interruptDelay.done && !reg.hcInterrupt.WDH.status
      unmaskedInterruptDelay := reg.hcInterrupt.WDH.enable
      when(token.wantExit) {
        goto(FRAME_NUMBER_CMD)
      }
    }
    FRAME_NUMBER_CMD.whenIsActive {
      ioDma.cmd.valid := True
      ioDma.cmd.address := reg.hcHCCA.HCCA.address | 0x80
      ioDma.cmd.length := 7
      ioDma.cmd.last := dmaWriteCtx.counter === 7 * 8 / p.dataWidth
      ioDma.cmd.setWrite()
      dmaWriteCtx.save(U"x0000" @@ reg.hcFmNumber.FN, 0, 0)
      when(doInterruptDelay) {
        dmaWriteCtx.save(reg.hcDoneHead.DH.address(31 downto 1) ## unmaskedInterruptDelay, 1, 0)
      }
      when(ioDma.cmd.ready && ioDma.cmd.last) {
        goto(FRAME_NUMBER_RSP)
      }
    }
    FRAME_NUMBER_RSP.whenIsActive {
      when(ioDma.rsp.valid) {
        reg.hcInterrupt.SF.status := True
        interruptDelay.tick := True
        when(doInterruptDelay) {
          reg.hcInterrupt.WDH.status := True
          reg.hcDoneHead.DH.reg := 0
          interruptDelay.disable := True
        }
        exitFsm()
      }
    }

    always{
      when(unscheduleAll.fire){ killFsm() }
    }
  }

  val FlowType = new SpinalEnum {
    val BULK, CONTROL, PERIODIC = newElement()
  }

  val priority = new Area {
    val bulk = Reg(Bool)
    val counter = Reg(UInt(2 bits))

    val tick = False //TODO
    val skip = False //TODO
    when(tick) {
      counter := counter + 1
      when(bulk || counter === reg.hcControl.CBSR) {
        skip := True
      }
    }

    when(skip) {
      bulk := !bulk
      counter := 0
    }
  }

  val interruptDelay = new Area {
    val counter = RegInit(U"111")
    val tick = False
    val done = counter === 0
    val disabled = counter === 7
    val disable = False

    val load = Flow(UInt(3 bits))
    load.valid := False
    load.payload assignDontCare()

    when(tick && !done && !disabled) {
      counter := counter - 1
    }

    when(load.valid && load.payload < counter) {
      counter := load.payload
    }

    when(disable) {
      counter := 7
    }
  }

  val rxPidOk = io.phy.rx.data(3 downto 0) === ~io.phy.rx.data(7 downto 4)

  val endpoint = new StateMachineSlave {
    val ED_READ_CMD, ED_READ_RSP, ED_ANALYSE = new State
    val TD_READ_CMD, TD_READ_RSP, TD_ANALYSE, TD_CHECK_TIME = new State
    val BUFFER_READ = new State
    val TOKEN = new State
    val DATA_TX, DATA_RX = new State
    val ACK_RX, ACK_TX = new State
    val UPDATE_TD_CMD = new State
    val UPDATE_ED_CMD, UPDATE_SYNC = new State
    val ABORD = new State

    setEntry(ED_READ_CMD)

    always{
      when(unscheduleAll.fire){ killFsm() }
    }

    val flowType = Reg(FlowType())

    val Status = new SpinalEnum {
      val OK, FRAME_TIME = newElement()
    }
    val status = Reg(Status)

    val ED = new Area {
      val address = Reg(UInt(32 bits))

      val words = Vec(Reg(Bits(32 bits)), 4)

      val FA = words(0)(0, 7 bits)
      val EN = words(0)(7, 4 bits)
      val D = words(0)(11, 2 bits)
      val S = words(0)(13)
      val K = words(0)(14)
      val F = words(0)(15)
      val MPS = words(0)(16, 11 bits).asUInt
      val tailP = words(1)(4, 28 bits).asUInt
      val H = words(2)(0)
      val C = words(2)(1)
      val headP = words(2)(4, 28 bits).asUInt
      val nextED = words(3)(4, 28 bits).asUInt

      val tdEmpty = tailP === headP
      val isFs = !S
      val isLs = S

      when(isStarted) {
        io.phy.lowSpeed := S
      }
    }

    val TD = new Area {
      val address = ED.headP << 4

      val words = Vec(Reg(Bits(32 bits)), 4)

      val CC = words(0)(28, 4 bits)
      val EC = words(0)(26, 2 bits)
      val T = words(0)(24, 2 bits)
      val DI = words(0)(21, 3 bits).asUInt
      val DP = words(0)(19, 2 bits)
      val R = words(0)(18, 1 bits)

      val CBP = words(1)(0, 32 bits).asUInt
      val nextTD = words(2)(4, 28 bits)
      val BE = words(3)(0, 32 bits).asUInt

      val pageMatch = CBP(12, 20 bits) === BE(12, 20 bits)

      val isIn = DP === 2
    }

    val codes = new {
      val noError = B"0000"
      val crc = B"0001"
      val bitStuffing = B"0010"
      val dataToggleMismatch = B"0011"
      val stall = B"0100"
      val deviceNotResponding = B"0101"
      val pidCheckFailure = B"0110"
      val unexpectedPid = B"0111"
      val dataOverrun = B"1000"
      val dataUnderrun = B"1001"
      val bufferOverrun = B"1100"
      val bufferUnderrun = B"1101"
      val notAccessed = M"111-"
    }


    val applyNextED = False
    when(applyNextED) {
      switch(flowType) {
        is(FlowType.BULK) {
          reg.hcBulkCurrentED.BCED.reg := ED.nextED
        }
        is(FlowType.CONTROL) {
          reg.hcControlCurrentED.CCED.reg := ED.nextED
        }
        is(FlowType.PERIODIC) {
          reg.hcPeriodCurrentED.PCED.reg := ED.nextED
        }
      }
    }

    onStart {
      status := Status.OK
    }

    ED_READ_CMD.whenIsActive {
      ioDma.cmd.valid := True
      ioDma.cmd.address := ED.address
      ioDma.cmd.length := 15
      ioDma.cmd.last := True
      ioDma.cmd.setRead()
      when(ioDma.cmd.ready) {
        goto(ED_READ_RSP)
      }
    }

    ED_READ_RSP.whenIsActive {
      dmaReadCtx.load(ED.words(0), 0, 0)
      dmaReadCtx.load(ED.words(1), 1, 0)
      dmaReadCtx.load(ED.words(2), 2, 0)
      dmaReadCtx.load(ED.words(3), 3, 0)

      when(ioDma.rsp.valid && ioDma.rsp.last) {
        goto(ED_ANALYSE)
      }
    }

    ED_ANALYSE.whenIsActive {
      when(ED.H || ED.S || ED.tdEmpty) { //Skip
        applyNextED := True
        exitFsm()
      } otherwise {
        goto(TD_READ_CMD)
      }
    }

    TD_READ_CMD.whenIsActive {
      //Fetch TD
      ioDma.cmd.valid := True
      ioDma.cmd.address := TD.address
      ioDma.cmd.length := (ED.F ? U(31) | U(15)).resized
      ioDma.cmd.last := True
      ioDma.cmd.setRead()
      when(ioDma.cmd.ready) {
        goto(TD_READ_RSP)
      }
    }

    TD_READ_RSP whenIsActive {
      dmaReadCtx.load(TD.words(0), 0, 0)
      dmaReadCtx.load(TD.words(1), 1, 0)
      dmaReadCtx.load(TD.words(2), 2, 0)
      dmaReadCtx.load(TD.words(3), 3, 0)
      when(ioDma.rsp.lastFire) {
        goto(TD_ANALYSE)
      }
    }

    val currentAddress = Reg(UInt(14 bits)) //One extra bit to allow overflow comparison
    val currentAddressFull = (currentAddress.msb ? TD.BE(31 downto 12) | TD.CBP(31 downto 12)) @@ currentAddress(11 downto 0)
    val lastAddress = Reg(UInt(13 bits))
    val transferSizeCalc = (U(TD.pageMatch) @@ TD.BE(0, 12 bits)) - TD.CBP(0, 12 bits)
    val transactionSize = lastAddress - currentAddress
    val zeroLength = Reg(Bool)
    val dataPhase = Reg(Bool)
    val dataDone = zeroLength || currentAddress > lastAddress

    val dmaLogic = new StateMachine {
      val INIT, CALC, CMD = new State
      setEntry(INIT)
      disableAutoStart()

      always{
        when(unscheduleAll.fire){ killFsm() }
      }

      val save = RegInit(False) //TODO what happen if True outside the expected context ?
      val length = Reg(UInt(p.dmaLengthWidth bits))
      val lengthMax = ~currentAddress.resize(p.dmaLengthWidth)
      val lengthCalc = transactionSize.min(lengthMax).resize(widthOf(lengthMax))
      val predictedWriteCounter = Reg(UInt(log2Up(ramBurstCapacity) bits))


      // Implement buffer filling threshold
      val bufferReady = Reg(Bool)
      val readRspCounter = Reg(UInt(4 bits))
      val abord = Event
      abord.valid := False
      abord.ready := False

      onStart {
        bufferReady := False
      }

      when(ioDma.rsp.fire) {
        readRspCounter := readRspCounter + 1
      }
      when(readRspCounter === readRspCounter.maxValue || (isStopped && dmaCtx.pendingCounter === 0)) {
        bufferReady := True
      }

      // Implement internal buffer write
      when(save && ioDma.rsp.valid) {
        dataBuffer.write.cmd.valid := True
        dataBuffer.write.cmd.data := ioDma.rsp.data
        dataBuffer.write.counter.increment := True
      }

      //read
      val readCmdEnable = RegInit(False)
      val readRspEnable = RegInit(False)
      val readByteCounter = Reg(UInt(13 bits))

      case class ReadCtx() extends Bundle {
        val last = Bool()
        val sel = UInt(log2Up(p.dataWidth / 8) bits)
      }

      val readCmdCtx, readRspCtx = ReadCtx()
      readRspCtx.assignFromBits(dataBuffer.read.rsp.linked)

      readCmdCtx.last := readByteCounter === lastAddress
      readCmdCtx.sel := readByteCounter.resized

      when(readCmdEnable) {
        when(bufferReady) {
          dataBuffer.read.cmd.valid := True
          dataBuffer.read.cmdCtx := readCmdCtx.asBits
          when(dataBuffer.read.cmd.ready) {
            readByteCounter := readByteCounter + 1
            when(readCmdCtx.sel.andR) {
              dataBuffer.read.counter.increment := True
            }
            when(readCmdCtx.last) {
              dataBuffer.read.cmdCtx(0) := True
              readCmdEnable := False
            }
          }
        }
      }

      when(readRspEnable) {
        dataTx.data.fragment := dataBuffer.read.rsp.value.subdivideIn(8 bits).read(readRspCtx.sel)
        dataTx.data.last := readRspCtx.last
        when(dataBuffer.read.rsp.valid) {
          dataTx.data.valid := True
          when(dataTx.data.ready) {
            dataBuffer.read.rsp.ready := True
            when(readRspCtx.last) {
              readRspEnable := False
            }
          }
        }
      }


      INIT whenIsActive {
        when(dataDone) {
          exitFsm()
        } otherwise {
          save := True
          predictedWriteCounter := 1
          dataBuffer.write.counter.clear := True

          readCmdEnable := True
          readRspEnable := True
          dataBuffer.read.counter.clear := True

          readRspCounter := 0

          goto(CALC)
        }
      }

      // Implement ioDma.cmd
      CALC whenIsActive {
        length := lengthCalc
        when(dataDone || abord.valid) {
          exitFsm()
        } otherwise {
          val checkShift = log2Up(p.dmaLength * 8 / p.dataWidth)
          when(predictedWriteCounter =/= (dataBuffer.read.counter.value >> checkShift) && !dmaCtx.pendingFull) {
            goto(CMD)
          }
        }
      }
      CMD whenIsActive {
        ioDma.cmd.last := True
        ioDma.cmd.setRead()
        ioDma.cmd.address := currentAddressFull
        ioDma.cmd.length := length
        ioDma.cmd.valid := True
        when(ioDma.cmd.ready) {
          currentAddress := currentAddress + lengthCalc + 1
          predictedWriteCounter := predictedWriteCounter + 1
          goto(CALC)
        }
      }

      val abordCounter = Reg(UInt(2 bits))
      when(abord.valid) {
        readCmdEnable := False
        readRspEnable := False
        dataBuffer.read.rsp.ready := True
        save := False
        abordCounter := abordCounter + 1
        abord.ready := abordCounter === 3
      }
      when(dataBuffer.read.rsp.valid || this.isStarted || !dmaCtx.pendingEmpty) {
        abordCounter := 0
      }
    }

    val currentAddressCalc = TD.CBP(0, 12 bits)
    val lastAddressNoSat = (U(!TD.pageMatch) @@ TD.BE(0, 12 bits))
    TD_ANALYSE.whenIsActive {
      switch(flowType) {
        is(FlowType.CONTROL) {
          reg.hcCommandStatus.CLF := True
        }
        is(FlowType.BULK) {
          reg.hcCommandStatus.BLF := True
        }
      }

      dmaLogic.readByteCounter := currentAddressCalc.resized
      currentAddress := currentAddressCalc.resized
      lastAddress := lastAddressNoSat.min(currentAddressCalc + ED.MPS - 1)

      zeroLength := TD.CBP === 0
      dataPhase := TD.T(1) ? TD.T(0) | ED.C

      dmaLogic.startFsm()
      goto(BUFFER_READ)
    }

    val byteCountCalc = lastAddress - currentAddress + 1

    BUFFER_READ.whenIsActive {
      when(ED.isFs && (byteCountCalc << 3) >= frame.limitCounter || (ED.isLs && reg.hcLSThreshold.hit)) {
        status := Status.FRAME_TIME
        goto(ABORD)
      } otherwise {
        when(TD.isIn || dmaLogic.bufferReady) {
          goto(TOKEN)
        }
      }
    }

    ABORD whenIsActive {
      dmaLogic.abord.valid := True
      when(dmaLogic.abord.ready) {
        exitFsm()
      }
    }


    TOKEN.onEntry {
      token.startFsm()
    }
    TOKEN.whenIsActive {
      token.data := ED.EN ## ED.FA
      switch((ED.D(0) =/= ED.D(1)) ? ED.D | TD.DP) {
        is(B"00") {
          token.pid := UsbPid.SETUP
        }
        is(B"01") {
          token.pid := UsbPid.OUT
        }
        is(B"10") {
          token.pid := UsbPid.IN
        }
      }
      when(token.wantExit) {
        when(TD.isIn){
          goto(DATA_RX)
        } otherwise {
          goto(DATA_TX)
        }
      }
    }

    DATA_TX.onEntry {
      dataTx.startFsm()
    }
    DATA_TX.whenIsActive {
      dataTx.pid := dataPhase ## B"011"
      when(dataTx.wantExit) {
        goto(ACK_RX)
        dmaLogic.save := False
      }
    }


    val rxCc = Reg(Bits(4 bits))
    ACK_RX.whenIsActive {
      when(io.phy.rx.valid){ //TODO manage errors, rx.errors and timeouts
        goto(UPDATE_TD_CMD)
        when(!rxPidOk){
          rxCc := codes.pidCheckFailure
        } otherwise{
          switch(io.phy.rx.data(3 downto 0)){
            is(UsbPid.ACK) { rxCc := codes.noError }
            is(UsbPid.NAK) { goto(UPDATE_SYNC)}
            is(UsbPid.STALL) { rxCc := codes.stall  }
            default( rxCc := codes.pidCheckFailure )
          }
        }
      }
    }

//    DATA_RX.onEntry{
//      dataRx.startFsm()
//    }
//    DATA_RX.whenIsActive {
//      dataRx.pid := dataPhase ## B"011"
//      when(dataRx.wantExit) {
//        goto(ACK_TX)
//        dmaLogic.save := False
//      }
//    }

    val tdCompletion = RegNext(currentAddress > lastAddressNoSat || zeroLength) //TODO zeroLength good enough ?
    UPDATE_TD_CMD.whenIsActive {
      ioDma.cmd.valid := True
      ioDma.cmd.address := TD.address
      ioDma.cmd.length := (ED.F ? U(31) | U(15)).resized
      ioDma.cmd.last := dmaWriteCtx.counter === (ED.F ? U(31 * 8 / p.dataWidth) | U(15 * 8 / p.dataWidth))
      ioDma.cmd.setWrite()
      //TODO manage rxCc
      dmaWriteCtx.save(codes.noError ## U"00" ## True ## !dataPhase, 0, 24)
      dmaWriteCtx.save(currentAddressFull, 1, 0)
      when(tdCompletion) {
        dmaWriteCtx.save(reg.hcDoneHead.DH.address, 2, 0)
        reg.hcDoneHead.DH.reg := ED.headP
      }
      when(ioDma.cmd.ready && ioDma.cmd.last) {
        goto(UPDATE_ED_CMD)
      }
    }

    UPDATE_ED_CMD.whenIsActive {
      ioDma.cmd.valid := True
      ioDma.cmd.address := ED.address
      ioDma.cmd.length := 15
      ioDma.cmd.last := dmaWriteCtx.counter === 15 * 8 / p.dataWidth
      ioDma.cmd.setWrite()
      //TODO
      when(tdCompletion) {
        dmaWriteCtx.save(TD.nextTD ## B"00" ## !dataPhase ## False, 2, 0)
      }
      when(ioDma.cmd.ready && ioDma.cmd.last) {
        goto(UPDATE_SYNC)
      }
    }

    UPDATE_SYNC.whenIsActive {
      when(dmaCtx.pendingEmpty) {
        switch(flowType) { //TODO
          is(FlowType.BULK) {
            reg.hcBulkCurrentED.BCED.reg := ED.nextED
            priority.tick := True
          }
          is(FlowType.CONTROL) {
            reg.hcControlCurrentED.CCED.reg := ED.nextED
            priority.tick := True
          }
          is(FlowType.PERIODIC) {
            reg.hcPeriodCurrentED.PCED.reg := ED.nextED
          }
        }

        when(tdCompletion) {
          interruptDelay.load.valid := True
          interruptDelay.load.payload := TD.DI
        }
        exitFsm()
      }
    }
  }


  val operational = new StateMachineSlave {
    val SOF, ARBITER, END_POINT, PERIODIC_HEAD_CMD, PERIODIC_HEAD_RSP, WAIT_SOF = new State
    setEntry(WAIT_SOF)


    val periodicHeadFetched = Reg(Bool)
    val periodicDone = Reg(Bool)

    val allowBulk = Reg(Bool)
    val allowControl = Reg(Bool)
    val allowPeriodic = Reg(Bool)
    val allowIsochronous = Reg(Bool)
    val askExit = False

    always{
      when(unscheduleAll.fire){ killFsm() }
    }

    onStart {
      allowPeriodic := False // Avoid overrun false positive trigger
      interruptDelay.disable := True
    }

    SOF.onEntry {
      sof.startFsm()
    }
    SOF.whenIsActive {
      when(sof.wantExit) {
        when(allowPeriodic && !periodicDone) { //Overrun condition
          reg.hcInterrupt.SO.status := True
          reg.hcCommandStatus.SOC := reg.hcCommandStatus.SOC + 1
        }

        allowBulk := reg.hcControl.BLE
        allowControl := reg.hcControl.CLE
        allowPeriodic := reg.hcControl.PLE
        allowIsochronous := reg.hcControl.IE

        periodicDone := False
        periodicHeadFetched := False
        priority.bulk := False
        priority.counter := 0
        goto(ARBITER)
      }
    }

    ARBITER.whenIsActive {
      allowBulk setWhen (reg.hcControl.BLE)
      allowControl setWhen (reg.hcControl.CLE)

      when(askExit){
        exitFsm()
      } elsewhen(frame.limitHit) {
        goto(WAIT_SOF)
      } elsewhen (allowPeriodic && !periodicDone && !frame.section1) {
        when(!periodicHeadFetched) {
          goto(PERIODIC_HEAD_CMD)
        } otherwise {
          when(reg.hcPeriodCurrentED.isZero) {
            periodicDone := True
          } otherwise {
            endpoint.flowType := FlowType.PERIODIC
            endpoint.ED.address := reg.hcPeriodCurrentED.PCED.address
            endpoint.startFsm()
            goto(END_POINT)
          }
        }
      } otherwise {
        priority.skip := True
        when(priority.bulk) {
          when(allowBulk) {
            when(reg.hcBulkCurrentED.isZero) {
              when(reg.hcCommandStatus.BLF) {
                reg.hcBulkCurrentED.BCED.reg := reg.hcBulkHeadED.BHED.reg
                reg.hcCommandStatus.BLF := False
                priority.skip := False
              }
            } otherwise {
              endpoint.flowType := FlowType.BULK
              endpoint.ED.address := reg.hcBulkCurrentED.BCED.address
              endpoint.startFsm()
              priority.skip := False
              goto(END_POINT)
            }
          }
        } otherwise {
          when(allowControl) {
            when(reg.hcControlCurrentED.isZero) {
              when(reg.hcCommandStatus.CLF) {
                reg.hcControlCurrentED.CCED.reg := reg.hcControlHeadED.CHED.reg
                reg.hcCommandStatus.CLF := False
                priority.skip := False
              }
            } otherwise {
              endpoint.flowType := FlowType.CONTROL
              endpoint.ED.address := reg.hcControlCurrentED.CCED.address
              endpoint.startFsm()
              priority.skip := False
              goto(END_POINT)
            }
          }
        }
      }
    }

    END_POINT.whenIsActive {
      when(endpoint.wantExit) {
        switch(endpoint.status) {
          is(endpoint.Status.OK) {
            goto(ARBITER)
          }
          is(endpoint.Status.FRAME_TIME) {
            goto(WAIT_SOF)
          }
        }
      }
    }

    PERIODIC_HEAD_CMD.whenIsActive {
      ioDma.cmd.valid := True
      ioDma.cmd.address := reg.hcHCCA.HCCA.address | (reg.hcFmNumber.FN(4 downto 0) << 2).resized
      ioDma.cmd.length := 3
      ioDma.cmd.last := True
      ioDma.cmd.setRead()
      when(ioDma.cmd.ready) {
        goto(PERIODIC_HEAD_RSP)
      }
    }

    PERIODIC_HEAD_RSP.whenIsActive {
      dmaRspMux.sel := reg.hcFmNumber.FN.resized
      when(ioDma.rsp.valid) {
        periodicHeadFetched := True
        reg.hcPeriodCurrentED.PCED.load(dmaRspMux.data)
        goto(ARBITER)
      }
    }


    WAIT_SOF.whenIsActive {
      when(frame.tick) {
        goto(SOF)
      }
    }
  }

  val hc = new StateMachine {
    val RESET, RESUME, OPERATIONAL, SUSPEND, ANY_TO_RESET, ANY_TO_SUSPEND = new State
    setEntry(RESET)

    reg.hcControl.HCFS := MainState.RESET


    io.phy.usbReset   := reg.hcControl.HCFS === MainState.RESET
    io.phy.usbResume  := reg.hcControl.HCFS === MainState.RESUME

    val error = False
    RESET.whenIsActive {
      when(reg.hcControl.HCFSWrite.valid) {
        switch(reg.hcControl.HCFSWrite.payload) {
          is(MainState.OPERATIONAL) {
            goto(OPERATIONAL)
          }
          default {
            error := True
          }
        }
      }
    }


    OPERATIONAL.onEntry {
      operational.startFsm()
      frame.reload := True
      bitTimer.reload := True
    }
    OPERATIONAL.whenIsActive {
      reg.hcControl.HCFS := MainState.OPERATIONAL
      frame.run := True
    }


    RESUME.whenIsActive {
      reg.hcControl.HCFS := MainState.RESUME
      when(reg.hcControl.HCFSWrite.valid && reg.hcControl.HCFSWrite.payload === MainState.OPERATIONAL) {
        goto(OPERATIONAL)
      }
    }


    SUSPEND.whenIsActive {
      reg.hcControl.HCFS := MainState.SUSPEND
      when(reg.hcRhStatus.DRWE && reg.hcRhPortStatus.map(_.CSC.reg).orR) {
        reg.hcInterrupt.RD.status := True
        goto(RESUME)
      }
    }

    ANY_TO_RESET onEntry{
      doUnschedule := True
    }
    ANY_TO_RESET whenIsActive{
      ctrlHalt := True
      reg.hcControl.HCFS := MainState.RESET
      when(!doUnschedule){
        goto(RESET)
      }
    }

    val operationalIsDone = operational.isStopped
    ANY_TO_SUSPEND onEntry {
      doUnschedule := True
    }
    ANY_TO_SUSPEND whenIsActive{
      ctrlHalt := True
      operational.askExit := True
      reg.hcControl.HCFS := MainState.SUSPEND
      when(!doUnschedule && !doSoftReset && operationalIsDone){
        goto(SUSPEND)
      }
    }

    always{
      // Handle HCD asking a usbRESET transition
      when(reg.hcControl.HCFSWrite.valid && reg.hcControl.HCFSWrite.payload === MainState.RESET) {
        goto(ANY_TO_RESET)
      }

      // Handle software reset
      when(reg.hcCommandStatus.startSoftReset){
        doSoftReset := True
        goto(ANY_TO_SUSPEND)
      }
    }
  }
}

/*
TODO
 READ
 notAccessed
 rx timeout ?
 suspend / resume
 time budget for low speed over high speed packet
 protect port exiting reset in the middle of something else
 tx end of packet of zero byte check
 6.5.7 RootHubStatusChange Event
 Likewise, the Root Hub must wait 5 ms after the Host Controller enters U SB S USPEND before generating a local wakeup event and forcing a transition to U SB R ESUME
 */