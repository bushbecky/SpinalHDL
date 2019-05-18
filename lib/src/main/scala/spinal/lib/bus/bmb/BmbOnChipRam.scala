package spinal.lib.bus.bmb

import spinal.core._
import spinal.lib.misc.HexTools
import spinal.lib.slave



object BmbOnChipRam{
  def busCapabilities(size : BigInt, dataWidth : Int) = BmbParameter(
    addressWidth  = log2Up(size),
    dataWidth     = dataWidth,
    lengthWidth   = log2Up(dataWidth/8),
    sourceWidth   = Int.MaxValue,
    contextWidth  = Int.MaxValue,
    canRead       = true,
    canWrite      = true,
    allowUnalignedWordBurst = false,
    allowUnalignedByteBurst = false,
    maximumPendingTransactionPerId = Int.MaxValue
  )
}

case class BmbOnChipRam(p: BmbParameter,
                        size : BigInt,
                        hexOffset : BigInt = null,
                        hexInit : String = null) extends Component {
  val io = new Bundle {
    val bus = slave(Bmb(p))
  }

  val ram = Mem(Bits(32 bits), size / 4)
  io.bus.cmd.ready   := !io.bus.rsp.isStall
  io.bus.rsp.valid   := RegNextWhen(io.bus.cmd.valid,   io.bus.cmd.ready) init(False)
  io.bus.rsp.source  := RegNextWhen(io.bus.cmd.source,  io.bus.cmd.ready)
  io.bus.rsp.context := RegNextWhen(io.bus.cmd.context, io.bus.cmd.ready)
  io.bus.rsp.data := ram.readWriteSync(
    address = (io.bus.cmd.address >> 2).resized,
    data  = io.bus.cmd.data,
    enable  = io.bus.cmd.fire,
    write  = io.bus.cmd.isWrite,
    mask  = io.bus.cmd.mask
  )
  io.bus.rsp.setSuccess()
  io.bus.rsp.last := True

  if(hexInit != null){
    assert(hexOffset != null)
    HexTools.initRam(ram, hexInit, hexOffset)
  }
}



case class BmbOnChipRamMultiPort( portsParameter: Seq[BmbParameter],
                                  size : BigInt,
                                  hexOffset : BigInt = null,
                                  hexInit : String = null) extends Component {
  val io = new Bundle {
    val buses = Vec(portsParameter.map(p => slave(Bmb(p))))
  }

  val ram = Mem(Bits(32 bits), size / 4)
  
  for(bus <- io.buses) {
    bus.cmd.ready := !bus.rsp.isStall
    bus.rsp.valid := RegNextWhen(bus.cmd.valid, bus.cmd.ready) init (False)
    bus.rsp.source := RegNextWhen(bus.cmd.source, bus.cmd.ready)
    bus.rsp.context := RegNextWhen(bus.cmd.context, bus.cmd.ready)
    bus.rsp.data := ram.readWriteSync(
      address = (bus.cmd.address >> 2).resized,
      data = bus.cmd.data,
      enable = bus.cmd.fire,
      write = bus.cmd.isWrite,
      mask = bus.cmd.mask
    )
    bus.rsp.setSuccess()
    bus.rsp.last := True
  }

  if(hexInit != null){
    assert(hexOffset != null)
    HexTools.initRam(ram, hexInit, hexOffset)
  }
}


