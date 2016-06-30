/******************************************************************************
  * This file describes the AXI4-Lite interface
  *
  * Interface :
  *   _______________________________________________________________________
  *  | Global  | Write Addr | Write Data | Write Rsp | Read Addr | Read Data |
  *  |   -     |    aw      |     wr     |     b     |    ar     |     r     |
  *  |----------------------- -----------------------------------------------|
  *  | aclk    | awvalid    | wvalid     | bvalid    | arvalid   | rvalid    |
  *  | arstn   | awready    | wready     | bready    | arready   | rready    |
  *  |         | awaddr     | wdata      | bresp     | araddr    | rdata     |
  *  |         | awprot     | wstrb      |           | arprot    | rresp     |
  *  |_________|____________|____________|___________|___________|___________|
  *
  */

package spinal.lib.bus.amba4.axilite

import spinal.core._
import spinal.lib._


/**
  * Definition of the constants used by the AXI Lite bus
  */
object AxiLite4 {

  /**
    * Read Write response
    */
  object resp{
    def apply() = Bits(2 bits)
    def OKAY   = B"00" // Normal access success
    def EXOKAY = B"01" // Exclusive access okay
    def SLVERR = B"10" // Slave error
    def DECERR = B"11" // Decode error
  }

  /**
    * Access permissions
    */
  object prot{
    def apply() = Bits(3 bits)
    def UNPRIVILEGED_ACCESS = B"000"
    def PRIVILEGED_ACCESS   = B"001"
    def SECURE_ACCESS       = B"000"
    def NON_SECURE_ACCESS   = B"010"
    def DATA_ACCESS         = B"000"
    def INSTRUCTION_ACCESS  = B"100"
  }
}


/**
  * Define all access modes
  */
trait AxiLite4Mode{
  def write = false
  def read = false
}
object WRITE_ONLY extends AxiLite4Mode{
  override def write = true
}
object READ_ONLY extends AxiLite4Mode{
  override def read = true
}
object READ_WRITE extends AxiLite4Mode{
  override def write = true
  override def read = true
}


/**
  * Configuration class for the Axi Lite bus
  * @param addressWidth Width of the address bus
  * @param dataWidth    Width of the data bus
  * @param mode         Access mode : WRITE_ONLY, READ_ONLY, READ_WRITE
  */
case class AxiLite4Config(addressWidth: Int,
                         dataWidth    : Int,
                         mode         : AxiLite4Mode = READ_WRITE){
  def dataByteCount = dataWidth/8
}


/**
  * Definition of the Write/Read address channel
  * @param config Axi Lite configuration class
  */
case class AxiLite4Ax(config: AxiLite4Config) extends Bundle {

  val addr = UInt(config.addressWidth bits)
  val prot = Bits(3 bits)


  import AxiLite4.prot._

  def setUnprivileged : Unit = prot := UNPRIVILEGED_ACCESS | SECURE_ACCESS | DATA_ACCESS
  def setPermissions ( permission : Bits ) : Unit = prot := permission
}


/**
  * Definition of the Write data channel
  * @param config Axi Lite configuration class
  */
case class AxiLite4W(config: AxiLite4Config) extends Bundle {
  val data = Bits(config.dataWidth bits)
  val strb = Bits(config.dataWidth / 8 bits)

  def setStrb : Unit = strb := (1 << widthOf(strb))-1
  def setStrb(bytesLane : Bits) : Unit = strb := bytesLane
}


/**
  * Definition of the Write response channel
  * @param config Axi Lite configuration class
  */
case class AxiLite4B(config: AxiLite4Config) extends Bundle {
  val resp = Bits(2 bits)

  import AxiLite4.resp._

  def setOKAY()   : Unit = resp := OKAY
  def setEXOKAY() : Unit = resp := EXOKAY
  def setSLVERR() : Unit = resp := SLVERR
  def setDECERR() : Unit = resp := DECERR
}


/**
  * Definition of the Read data channel
  * @param config Axi Lite configuration class
  */
case class AxiLite4R(config: AxiLite4Config) extends Bundle {
  val data = Bits(config.dataWidth bits)
  val resp = Bits(2 bits)

  import AxiLite4.resp._

  def setOKAY()   : Unit = resp := OKAY
  def setEXOKAY() : Unit = resp := EXOKAY
  def setSLVERR() : Unit = resp := SLVERR
  def setDECERR() : Unit = resp := DECERR
}


/**
  * Axi Lite interface definition
  * @param config Axi Lite configuration class
  */
case class AxiLite4(config: AxiLite4Config) extends Bundle with IMasterSlave {

  val aw = if(config.mode.write)  Stream(AxiLite4Ax(config)) else null
  val w  = if(config.mode.write)  Stream(AxiLite4W(config))  else null
  val b  = if(config.mode.write)  Stream(AxiLite4B(config))  else null
  val ar = if(config.mode.read)   Stream(AxiLite4Ax(config)) else null
  val r  = if(config.mode.read)   Stream(AxiLite4R(config))  else null

  //Because aw w b ar r are ... very lazy
  def writeCmd  = aw
  def writeData = w
  def writeRsp  = b
  def readCmd   = ar
  def readRsp   = r


  def >> (that : AxiLite4) : Unit = {
    assert(that.config == this.config)

    if(config.mode.write){
      this.writeCmd  >> that.writeCmd
      this.writeData >> that.writeData
      this.writeRsp  << that.writeRsp
    }

    if(config.mode.read) {
      this.readCmd  >> that.readCmd
      this.readRsp << that.readRsp
    }
  }

  def <<(that : AxiLite4) : Unit = that >> this

  override def asMaster(): this.type = {
    if(config.mode.write){
      master(aw,w)
      slave(b)
    }
    if(config.mode.read) {
      master(ar)
      slave(r)
    }
    this
  }
}
