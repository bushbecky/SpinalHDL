/*
 * SpinalHDL
 * Copyright (c) Dolu, All rights reserved.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3.0 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.
 */

package spinal.core

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer

/**
  * Created by PIC18F on 02.02.2015.
  */
trait ReadUnderWritePolicy{
  def readUnderWriteString : String
}

trait MemTechnologyKind{
  def technologyKind : String
}

object dontCare extends ReadUnderWritePolicy{
  override def readUnderWriteString: String = "dontCare"
}

object writeFirst extends ReadUnderWritePolicy {
  override def readUnderWriteString: String = "writeFirst"
}

object readFirst extends ReadUnderWritePolicy {
  override def readUnderWriteString: String = "readFirst"
}

object auto extends  MemTechnologyKind{
  override def technologyKind: String = "auto"
}


object ramBlock extends MemTechnologyKind {
  override def technologyKind: String = "ramBlock"
}

object distributedLut extends MemTechnologyKind {
  override def technologyKind: String = "distributedLut"
}

object registerFile extends MemTechnologyKind {
  override def technologyKind: String = "registerFile"
}


object Mem {
  def apply[T <: Data](wordType: T, wordCount: Int) = new Mem(wordType, wordCount)
  def apply[T <: Data](wordType: T, wordCount: BigInt) = {
    assert(wordCount <= Integer.MAX_VALUE)
    new Mem(wordType, wordCount.toInt)
  }
  def apply[T <: Data](wordType: T, initialContent: Seq[T]) = new Mem(wordType, initialContent.length) init (initialContent)
  def apply[T <: Data](initialContent: Seq[T]) = new Mem(initialContent(0), initialContent.length) init (initialContent)
}

class MemWritePayload[T <: Data](dataType: T, addressWidth: Int) extends Bundle {
  val data = cloneOf(dataType)
  val address = UInt(addressWidth bit)
}

//case class MemWriteOrReadSync(write : MemReadWrite_writePart,read : MemReadWrite_readPart)

object AllowMixedWidth extends SpinalTag
trait MemPortStatement extends LeafStatement with StatementDoubleLinkedContainerElement[Mem[_], MemPortStatement]

class Mem[T <: Data](_wordType: T, val wordCount: Int) extends DeclarationStatement with StatementDoubleLinkedContainer[Mem[_], MemPortStatement] with WidthProvider with SpinalTagReady{
  if(component != null) dslContext.scope.append(this)
//  var forceMemToBlackboxTranslation = false
  val _widths = _wordType.flatten.map(t => t.getBitsWidth).toVector //Force to fix width of each wire
  val width = _widths.reduce(_ + _)
  def wordType: T = cloneOf(_wordType)



//  var technology : MemTechnologyKind = auto
//  def setTechnology(tech : MemTechnologyKind) = this.technology = tech
//
//  val ports = ArrayBuffer[Any]()
//  def getWritePorts() = ports.filter(_.isInstanceOf[MemWrite]).map(_.asInstanceOf[MemWrite])
//  def getReadSyncPorts() = ports.filter(_.isInstanceOf[MemReadSync]).map(_.asInstanceOf[MemReadSync])
//  def getReadAsyncPorts() = ports.filter(_.isInstanceOf[MemReadAsync]).map(_.asInstanceOf[MemReadAsync])
//  def getMemWriteOrReadSyncPorts() = ports.filter(_.isInstanceOf[MemWriteOrReadSync]).map(_.asInstanceOf[MemWriteOrReadSync])



//  override def getTypeObject: Any = TypeBits
//  override def opName: String = "Mem"

  override val getWidth : Int = width

  def addressWidth = log2Up(wordCount)
  def generateAsBlackBox(): this.type = ???
//  def generateAsBlackBox(): this.type = {
//    forceMemToBlackboxTranslation = true
//    this
//  }

  var initialContent : Array[BigInt] = null
//  private[core] def checkInferedWidth: Unit = {
//    val maxLit = (BigInt(1) << getWidth)-1
//    if(initialContent != null && initialContent.filter(v => v > maxLit || v < 0).nonEmpty){
//      PendingError(s"$this as some initial content values doesn't fit in memory words.\n${getScalaLocationLong}")
//    }
//  }


  def initBigInt(initialContent : Seq[BigInt]): this.type ={
    assert(initialContent.length == wordCount, s"The initial content array size (${initialContent.length}) is not equals to the memory size ($wordCount).\n" + this.getScalaLocationLong)
    this.initialContent = initialContent.toArray
    this
  }

  def init(initialContent: Seq[T]): this.type = {
    assert(initialContent.length == wordCount, s"The initial content array size (${initialContent.length}) is not equals to the memory size ($wordCount).\n" + this.getScalaLocationLong)
//    val bytePerWord = (getWidth + 7)/8
    this.initialContent = new Array[BigInt](initialContent.length)

    val widthsMasks = _widths.map(w => ((BigInt(1) << w) - 1))
    var nextOffset = 0
    val offsets = _widths.map(width => {
      val w = nextOffset
      nextOffset += width
      w
    })
    for((word,wordIndex) <- initialContent.zipWithIndex){
      val elements = word.flatten
      var builder = BigInt(0)
      for(elementId <- (0 until _widths.length)) {
        val element = elements(elementId)
        val offset = offsets(elementId)
        val width = _widths(elementId)
        val mask = widthsMasks(elementId)
        def walk(that : BaseType) : Unit = that.head match {
          case AssignementStatement(_, literal : Literal) if element.hasOnlyOneStatement =>
            val value = (((literal match {
              case literal : EnumLiteral[_] => elements(elementId).asInstanceOf[SpinalEnumCraft[_]].encoding.getValue(literal.enum)
              case literal : Literal => literal.getValue()
            }) & mask) << offset)

            builder += value
          case AssignementStatement(_, input : BaseType) if element.hasOnlyOneStatement  => walk(input)
          case _ => SpinalError("ROM initial value should be provided from full literals value")
        }
        walk(element)
      }
      this.initialContent(wordIndex) = builder
    }
    this
  }

  def apply(address: UInt): T = {
    val ret = readAsync(address)

    ret.compositeAssign = new Assignable {
      override private[core] def assignFromImpl(that: AnyRef, target: AnyRef, kind: AnyRef): Unit = {
        write(address, that.asInstanceOf[T])
      }

      override def getRealSourceNoRec: Any = Mem.this
    }
    ret
  }

  def addressType = UInt(addressWidth bit)

//  def addressTypeAt(initialValue: BigInt) = U(initialValue, addressWidth bit)
//
//  private def addPort(port : Node with Nameable) : Unit = {
//    port.setPartialName("port" + ports.length,true)
//    port.setRefOwner(this)
//    ports += port
//  }
//
//
  def readAsync(address: UInt, readUnderWrite: ReadUnderWritePolicy = dontCare): T = {
    val readWord = cloneOf(wordType)
    readAsyncImpl(address,readWord,readUnderWrite,false)
    readWord
  }
//
//  def readAsyncMixedWidth(address: UInt, data : Data, readUnderWrite: ReadUnderWritePolicy = dontCare): Unit =  readAsyncImpl(address,data,readUnderWrite,true)
//
  def readAsyncImpl(address: UInt, data : Data,readUnderWrite : ReadUnderWritePolicy = dontCare,allowMixedWidth : Boolean): Unit = {
    val readBits = (if(allowMixedWidth) Bits() else Bits(getWidth bits))

    val readPort = MemReadAsync(this, address, data.getBitsWidth, readUnderWrite)
    if(allowMixedWidth) readPort.addTag(AllowMixedWidth)

    this.dslContext.scope.append(readPort)
    this.dlcAppend(readPort)

    readBits.assignFrom(readPort)
    data.assignFromBits(readBits)
  }

  def readSync(address: UInt, enable: Bool = null, readUnderWrite: ReadUnderWritePolicy = dontCare, clockCrossing: Boolean = false): T = {
    val readWord = wordType
    readSyncImpl(address,readWord,enable,readUnderWrite,clockCrossing,false)
    readWord
  }

  def readSyncMixedWidth(address: UInt, data : Data, enable: Bool = null,readUnderWrite: ReadUnderWritePolicy = dontCare,clockCrossing: Boolean = false): Unit =  readSyncImpl(address,data,enable,readUnderWrite,clockCrossing,true)

  def readSyncImpl(address: UInt, data : Data, enable: Bool = null, readUnderWrite: ReadUnderWritePolicy = dontCare, clockCrossing: Boolean = false,allowMixedWidth : Boolean = false): Unit = {
    val readBits = (if(allowMixedWidth) Bits() else Bits(getWidth bits))

    val readPort = MemReadSync(this, address, data.getBitsWidth, if(enable != null) enable else True, readUnderWrite, ClockDomain.current)
    if(allowMixedWidth) readPort.addTag(AllowMixedWidth)
    if(clockCrossing) readPort.addTag(crossClockDomain)

    this.dslContext.scope.append(readPort)
    this.dlcAppend(readPort)

    readBits.assignFrom(readPort)
    data.assignFromBits(readBits)
  }

  @deprecated
  def readSyncCC(address: UInt, enable: Bool = True, readUnderWrite: ReadUnderWritePolicy = dontCare): T = {
    readSync(address, enable, readUnderWrite, true)
  }


  def writeMixedWidth(address: UInt, data: Data,enable : Bool = null, mask: Bits = null): Unit = writeImpl(address,data,enable,mask,allowMixedWidth = true)
  def write(address: UInt, data: T,enable : Bool = null, mask: Bits = null) : Unit = writeImpl(address,data,enable,mask,allowMixedWidth = false)

  def writeImpl(address: UInt, data: Data,enable : Bool = null, mask: Bits = null,allowMixedWidth : Boolean = false) : Unit = {

    val whenCond =  if(enable == null) ConditionalContext.isTrue else enable
    val writePort = MemWrite(this, address, data.asBits, mask,whenCond, if(allowMixedWidth) data.getBitsWidth else getWidth ,ClockDomain.current)
    this.dslContext.scope.append(writePort)
    this.dlcAppend(writePort)


    //    if(allowMixedWidth) writePort.addTag(AllowMixedWidth)
//    val addressBuffer = (if(allowMixedWidth) UInt() else UInt(addressWidth bits)).dontSimplifyIt() //Allow resized address when mixedMode is disable
//    addressBuffer := address
//    val dataBuffer = (if(allowMixedWidth) Bits() else Bits(getWidth bits)).dontSimplifyIt()
//    dataBuffer := data.asBits
//
//    val maskBuffer = if (mask != null) {
//      val ret = Bits().dontSimplifyIt()
//      ret := mask
//      ret
//    } else {
//      null
//    }
//
//    val whenCond =  if(enable == null) when.getWhensCond(this) else enable
//    val whenBuffer = Bool.dontSimplifyIt()
//    whenBuffer := whenCond
//    val writePort = new MemWrite(this, addressBuffer, dataBuffer, maskBuffer,whenBuffer, ClockDomain.current)
//    if(allowMixedWidth) writePort.addTag(AllowMixedWidth)
//    inputs += writePort
//
//    addressBuffer.setRefOwner(writePort)
//    addressBuffer.setPartialName("address",true)
//
//    dataBuffer.setRefOwner(writePort)
//    dataBuffer.setPartialName("data",true)
//
//    if(maskBuffer != null) {
//      maskBuffer.setRefOwner(writePort)
//      maskBuffer.setPartialName("mask", true)
//    }
//
//    whenBuffer.setRefOwner(writePort)
//    whenBuffer.setPartialName("enable",true)
//
//    addPort(writePort)
  }
//
  // Single port ram
  def readWriteSync (address: UInt,
                     data: T,
                     enable: Bool,
                     write: Bool,
                     mask: Bits = null,
                     readUnderWrite: ReadUnderWritePolicy = dontCare,
                     clockCrossing: Boolean = false): T = {
    readWriteSyncImpl(address,data,enable,write,mask,readUnderWrite,clockCrossing,false)
  }

  def readWriteSyncMixedWidth[U <: Data](address: UInt,
                               data: U,
                               enable: Bool,
                               write: Bool,
                               mask: Bits = null,
                               readUnderWrite: ReadUnderWritePolicy = dontCare,
                               clockCrossing: Boolean = false): U = {
    readWriteSyncImpl(address,data,enable,write,mask,readUnderWrite,clockCrossing,true)
  }

  def readWriteSyncImpl[U <: Data](address: UInt,
                                   data: U,
                                   enable: Bool,
                                   write: Bool,
                                   mask: Bits = null,
                                   readUnderWrite: ReadUnderWritePolicy = dontCare,
                                   clockCrossing: Boolean = false,
                                   allowMixedWidth : Boolean = false): U = {
    ???
//    val addressBuffer = (if(allowMixedWidth) UInt() else UInt(addressWidth bits)).dontSimplifyIt() //Allow resized address when mixedMode is disable
//    addressBuffer := address
//    val dataBuffer = (if(allowMixedWidth) Bits() else Bits(getWidth bits)).dontSimplifyIt()
//    dataBuffer := data.asBits
//
//    val enableBuffer = Bool.dontSimplifyIt()
//    enableBuffer := enable
//
//    val writeBuffer = Bool.dontSimplifyIt()
//    writeBuffer := write
//
//    val maskBuffer = if (mask != null) {
//      val ret = Bits().dontSimplifyIt()
//      ret := mask
//      ret
//    } else {
//      null
//    }
//
//    val writePort = new MemReadWrite_writePart(this, addressBuffer, dataBuffer,maskBuffer, enableBuffer, writeBuffer, ClockDomain.current)
//    if(allowMixedWidth) writePort.addTag(AllowMixedWidth)
//    inputs += writePort
//
//    enableBuffer.setPartialName(writePort,"enable",true)
//    writeBuffer.setPartialName(writePort,"write",true)
//    addressBuffer.setPartialName(writePort,"address",true)
//    dataBuffer.setPartialName(writePort,"writeData",true)
//
//    if(maskBuffer != null) {
//      maskBuffer.setPartialName(writePort,"mask", true)
//    }
//
//    val readBits = (if(allowMixedWidth) Bits() else Bits(getWidth bits)).dontSimplifyIt()
//    val readWord = cloneOf(data)
//    val readPort = new MemReadWrite_readPart(this, addressBuffer, readBits, enableBuffer, writeBuffer, readUnderWrite, ClockDomain.current)
//    if(allowMixedWidth) readPort.addTag(AllowMixedWidth)
//
//    readBits.input = readPort
//    readBits.setPartialName(readPort,"readData",true)
//
//    readWord.assignFromBits(readBits)
//    if (clockCrossing)
//      readPort.addTag(crossClockDomain)
//
//
//    writePort.readPart = readPort;
//    readPort.writePart = writePort
//
//    readPort.setPartialName(this,"port" + ports.length,true)
//    writePort.setPartialName(this,"port" + ports.length,true)
//    ports += MemWriteOrReadSync(writePort,readPort)
//
//    readWord
  }

  override def addAttribute(attribute: Attribute): this.type = addTag(attribute)
//

  private[core] def getMemSymbolWidth() : Int = {
    var symbolWidth = getWidth
    var symbolWidthSet = false
    this.foreachStatements{
      case port : MemWrite => {
        if(port.mask != null){
          val portSymbolWidth = getWidth/port.mask.getWidth
          if(symbolWidthSet){
            if(symbolWidth != portSymbolWidth) SpinalError(s"Mem with different asspect ratio at\n${this.getScalaLocationLong}")
          }else{
            symbolWidth = portSymbolWidth
            symbolWidthSet = true
          }
        }
      }
//      case port : MemReadWrite_writePart => {
//        if(port.getMask != null){
//          val portSymbolWidth = getWidth/port.getMask.getWidth
//          if(symbolWidthSet){
//            if(symbolWidth != portSymbolWidth) SpinalError(s"Mem with different asspect ratio at\n${this.getScalaLocationLong}")
//          }else{
//            symbolWidth = portSymbolWidth
//            symbolWidthSet = true
//          }
//        }
//      }
      case port : MemReadSync =>
      case port : MemReadAsync =>
    }
    symbolWidth
  }
  private[core] def getMemSymbolCount() : Int = getWidth/getMemSymbolWidth

  def randBoot(): this.type = {
    addTag(spinal.core.randomBoot)
    this
  }

//  override def toString(): String = s"${component.getPath() + "/" + this.getDisplayName()} : ${getClassIdentifier}[${getWidth} bits]"
}

object MemReadAsync{
  def apply( mem     : Mem[_],
             address : Expression with WidthProvider,
             width : Int,
             readUnderWrite: ReadUnderWritePolicy): MemReadAsync  = {
    val port = new MemReadAsync
    port.width = width
    port.readUnderWrite = readUnderWrite
    port.address = address
    port.mem = mem
    port
  }
}

class MemReadAsync extends MemPortStatement with WidthProvider with SpinalTagReady with ContextUser with Expression{
  override def getWidth: Int = width

  var width : Int = -1
  var readUnderWrite: ReadUnderWritePolicy = dontCare
  var address : Expression with WidthProvider = null
  var mem     : Mem[_] = null



  override def opName = "Mem.readAsync(x)"

  override def getTypeObject = TypeBits

  override def dlcParent = mem

  override def addAttribute(attribute: Attribute): MemReadAsync.this.type = addTag(attribute)

  override def remapExpressions(func: (Expression) => Expression): Unit = {
    address = func(address).asInstanceOf[Expression with WidthProvider]
  }

  override def foreachExpression(func: (Expression) => Unit): Unit = {
    func(address)
  }

  override def normalizeInputs: Unit = {
    val addressReq = mem.addressWidth + log2Up(aspectRatio)
    address = InputNormalize.resizedOrUnfixedLit(address,addressReq,new ResizeUInt,address, this) //TODO better error messaging

    if (readUnderWrite == readFirst) PendingError(s"readFirst mode for asynchronous read is not allowed\n ${this.getScalaLocationLong}")

    if(mem.getWidth != getWidth){
      if(!hasTag(AllowMixedWidth)) {
        PendingError(s"Read data width (${getWidth} bits) is not the same than the memory one ($mem) at\n${this.getScalaLocationLong}")
        return
      }
      if(mem.getWidth / getWidth * getWidth != mem.getWidth) {
        PendingError(s"The aspect ration between readed data and the memory should be a power of two. currently it's ${mem.getWidth}/${getWidth}. Memory : $mem, written at\n${this.getScalaLocationLong}")
        return
      }
    }

    if(address.getWidth != mem.addressWidth + log2Up(aspectRatio)) {
      PendingError(s"Address used to read $mem doesn't match the required width, ${address.getWidth} bits in place of ${mem.addressWidth + log2Up(aspectRatio)} bits\n${this.getScalaLocationLong}")
      return
    }
  }

  def aspectRatio = mem.getWidth/getWidth
}

object MemReadSync{
  def apply(mem : Mem[_], address : UInt,  width: Int, enable : Bool, readUnderWrite: ReadUnderWritePolicy, clockDomain: ClockDomain): MemReadSync ={
    val port = new MemReadSync
    port.mem = mem
    port.address = address
    port.width = width
    port.readEnable = enable
    port.clockDomain = clockDomain
    port.readUnderWrite = readUnderWrite

    port
  }
}


class MemReadSync() extends MemPortStatement with WidthProvider with SpinalTagReady with ContextUser with Expression {
  var width : Int = -1
  var address : Expression with WidthProvider = null
  var readEnable  : Expression = null
  var mem     : Mem[_] = null
  var clockDomain: ClockDomain = null
  var readUnderWrite : ReadUnderWritePolicy = null

  override def addAttribute(attribute: Attribute): this.type = addTag(attribute)


  override def opName = "Mem.readSync(x)"

  override def getTypeObject = TypeBits

  override def remapExpressions(func: (Expression) => Expression): Unit = {
    address = func(address).asInstanceOf[Expression with WidthProvider]
    readEnable = func(readEnable)
  }

  override def foreachExpression(func: (Expression) => Unit): Unit = {
    func(address)
    func(readEnable)
  }



//  def useReadEnable: Boolean = {
//    val lit = getReadEnable.getLiteral[BoolLiteral]
//    return lit == null || lit.value == false
//  }
//
//  def sameAddressThan(write: MemWrite): Unit = {
//    //Used by backed to symplify
//    this.setInput(MemReadSync.getAddressId,write.getAddress)
//  }

  override def dlcParent = mem

  override def getWidth: Int = width //getMem.getWidth >> (address.getWidth - mem.addressWidth)


  override def normalizeInputs: Unit = {
    val addressReq = mem.addressWidth + log2Up(aspectRatio)
    address = InputNormalize.resizedOrUnfixedLit(address,addressReq,new ResizeUInt,address, this) //TODO better error messaging

    if(mem.getWidth != getWidth){
      if(!hasTag(AllowMixedWidth)) {
        PendingError(s"Read data width (${width} bits) is not the same than the memory one ($mem) at\n${this.getScalaLocationLong}")
        return
      }
      if(mem.getWidth / getWidth * getWidth != mem.getWidth) {
        PendingError(s"The aspect ration between readed data and the memory should be a power of two. currently it's ${mem.getWidth}/${getWidth}. Memory : $mem, written at\n${this.getScalaLocationLong}")
        return
      }
    }

    if(address.getWidth != mem.addressWidth + log2Up(aspectRatio)) {
      PendingError(s"Address used to read $mem doesn't match the required width, ${address.getWidth} bits in place of ${mem.addressWidth + log2Up(aspectRatio)} bits\n${this.getScalaLocationLong}")
      return
    }

  }

  def aspectRatio = mem.getWidth/getWidth
}


object MemWrite{
  def apply(mem: Mem[_], address : UInt, data : Bits, mask : Bits, enable : Bool, width : Int, clockDomain: ClockDomain) : MemWrite = {
    val ret = new MemWrite
    ret.mem = mem
    ret.address = address
    ret.mask = mask
    ret.writeEnable = enable
    ret.clockDomain = clockDomain
    ret.width = width
    ret.data = data
    ret
  }
}

class MemWrite() extends MemPortStatement with WidthProvider with SpinalTagReady {
  var mem      : Mem[_] = null
  var width     : Int = -1
  var address  : Expression with WidthProvider  = null
  var data     : Expression with WidthProvider = null
  var mask     : Expression with WidthProvider =  null
  var writeEnable  : Expression  = null
  var clockDomain: ClockDomain = null


  override def dlcParent = mem

  override def addAttribute(attribute: Attribute): this.type = addTag(attribute)

  override def getWidth = width

  override def remapExpressions(func: Expression => Expression): Unit = {
    address = func(address).asInstanceOf[Expression with WidthProvider]
    data = func(data).asInstanceOf[Expression with WidthProvider]
    if(mask != null) mask = func(mask).asInstanceOf[Expression with WidthProvider]
    writeEnable = func(writeEnable)
  }

  override def foreachExpression(func: Expression => Unit): Unit = {
    func(address)
    func(data)
    if(mask != null) func(mask)
    func(writeEnable)
  }


  override def foreachDrivingExpression(func: Expression => Unit): Unit = {
    func(address)
    func(data)
    if(mask != null) func(mask)
    func(writeEnable)
  }

  override def normalizeInputs: Unit = {
    val addressReq = mem.addressWidth + log2Up(aspectRatio)
    address = InputNormalize.resizedOrUnfixedLit(address,addressReq,new ResizeUInt,address, this) //TODO better error messaging


    if(mem.getWidth != getWidth){
      if(!hasTag(AllowMixedWidth)) {
        PendingError(s"Write data width (${data.getWidth} bits) is not the same than the memory one ($mem) at\n${this.getScalaLocationLong}")
        return
      }
      if(mem.getWidth / getWidth * getWidth != mem.getWidth) {
        PendingError(s"The aspect ration between written data and the memory should be a power of two. currently it's ${mem.getWidth}/${getWidth}. Memory : $mem, written at\n${this.getScalaLocationLong}")
        return
      }
    }

    if(mask != null && getWidth % mask.getWidth != 0) {
      PendingError(s"Memory write_data_width % write_data_mask_width != 0 at\n${this.getScalaLocationLong}")
      return
    }


    if(address.getWidth != addressReq) {
      PendingError(s"Address used to write $mem doesn't match the required width, ${address.getWidth} bits in place of ${mem.addressWidth + log2Up(aspectRatio)} bits\n${this.getScalaLocationLong}")
      return
    }
  }

  def aspectRatio = mem.getWidth/getWidth
}

//object MemReadWrite_writePart {
//  val getAddressId: Int = 4
//  val getDataId: Int = 5
//  val getChipSelectId: Int = 6
//  val getWriteEnableId: Int = 7
//  val getMaskId: Int = 8
//}
//
//class MemReadWrite_writePart(mem: Mem[_], address_ : UInt, data_ : Bits, mask_ : Bits, chipSelect_ : Bool, writeEnable_ : Bool, clockDomain: ClockDomain) extends SyncNode(clockDomain) with Widthable with CheckWidth with Nameable{
//  var address : Node with Widthable  = address_
//  var data     : Node with Widthable = data_
//  var mask     : Node with Widthable =  mask_
//  var chipSelect   : Node = chipSelect_
//  var writeEnable  : Node  = writeEnable_
//
//
//  override def addAttribute(attribute: Attribute): this.type = addTag(attribute)
//
//  override def onEachInput(doThat: (Node, Int) => Unit): Unit = {
//    super.onEachInput(doThat)
//    doThat(address,MemReadWrite_writePart.getAddressId)
//    doThat(data,MemReadWrite_writePart.getDataId)
//    doThat(chipSelect,MemReadWrite_writePart.getChipSelectId)
//    doThat(writeEnable,MemReadWrite_writePart.getWriteEnableId)
//    if(mask != null) doThat(mask,MemWrite.getMaskId)
//  }
//
//  override def onEachInput(doThat: (Node) => Unit): Unit = {
//    super.onEachInput(doThat)
//    doThat(address)
//    doThat(data)
//    doThat(chipSelect)
//    doThat(writeEnable)
//    if(mask != null) doThat(mask)
//  }
//
//  override def setInput(id: Int, node: Node): Unit = id match{
//    case MemReadWrite_writePart.getAddressId => address = node.asInstanceOf[Node with Widthable]
//    case MemReadWrite_writePart.getDataId => data = node.asInstanceOf[Node with Widthable]
//    case MemReadWrite_writePart.getChipSelectId => chipSelect = node
//    case MemReadWrite_writePart.getWriteEnableId => writeEnable = node
//    case MemReadWrite_writePart.getMaskId => mask = node.asInstanceOf[Node with Widthable]
//    case _ => super.setInput(id,node)
//  }
//
//  override def getInputsCount: Int = super.getInputsCount + 4 + (if(mask != null) 1 else 0)
//  override def getInputs: Iterator[Node] = super.getInputs ++ Iterator(address,data,chipSelect,writeEnable) ++ (if(mask != null) List(mask) else Nil)
//  override def getInput(id: Int): Node = id match{
//    case MemReadWrite_writePart.getAddressId => address
//    case MemReadWrite_writePart.getDataId => data
//    case MemReadWrite_writePart.getChipSelectId => chipSelect
//    case MemReadWrite_writePart.getWriteEnableId => writeEnable
//    case MemReadWrite_writePart.getMaskId => mask
//    case _ => super.getInput(id)
//  }
//
//  var readPart: MemReadWrite_readPart = null
//
//  override def getSynchronousInputs: List[Node] = {
//    val base = getAddress :: getData :: getChipSelect :: getWriteEnable :: super.getSynchronousInputs
//    if(mask != null)
//      mask :: base
//    else
//      base
//  }
//
//  override def isUsingResetSignal: Boolean = false
//  override def isUsingSoftResetSignal: Boolean = false
//
//  def getMem = mem
//  def getAddress = address.asInstanceOf[UInt]
//  def getData = data.asInstanceOf[Bits]
//  def getChipSelect = chipSelect.asInstanceOf[Bool]
//  def getWriteEnable = writeEnable.asInstanceOf[Bool]
//  def getMask: Bits = {
//    if (mask.isInstanceOf[Bits])
//      mask.asInstanceOf[Bits]
//    else
//      null
//  }
//
//  override def calcWidth: Int = data.getWidth
//
//
//  override private[core] def checkInferedWidth: Unit = {
//    if(mem.getWidth != getWidth){
//      if(!hasTag(AllowMixedWidth)) {
//        PendingError(s"Write data width (${data.getWidth} bits) is not the same than the memory one ($mem) at\n${this.getScalaLocationLong}")
//        return
//      }
//      if(mem.getWidth / getWidth * getWidth != mem.getWidth) {
//        PendingError(s"The aspect ration between written data and the memory should be a power of two. currently it's ${mem.getWidth}/${getWidth}. Memory : $mem, written at\n${this.getScalaLocationLong}")
//        return
//      }
//    }
//
//    if(getMask != null && getData.getWidth % getMask.getWidth != 0) {
//      PendingError(s"Memory write_data_width % write_data_mask_width != 0 at\n${this.getScalaLocationLong}")
//      return
//    }
//
//
//    if(address.getWidth != mem.addressWidth + log2Up(aspectRatio)) {
//      PendingError(s"Address used to write $mem doesn't match the required width, ${address.getWidth} bits in place of ${mem.addressWidth + log2Up(aspectRatio)} bits\n${this.getScalaLocationLong}")
//      return
//    }
//  }
//
//  def aspectRatio = mem.getWidth/getWidth
//}
//
//
//object MemReadWrite_readPart {
//  val getAddressId: Int = 4
//  val getChipSelectId: Int = 5
//  val getWriteEnableId: Int = 6
//  val getMemId: Int = 7
//}
//
//class MemReadWrite_readPart(mem_ : Mem[_], address_ : UInt, data_ : Bits, chipSelect_ : Bool, writeEnable_ : Bool, val readUnderWrite: ReadUnderWritePolicy, clockDomain: ClockDomain) extends SyncNode(clockDomain) with Widthable with CheckWidth with Nameable{
//
//  var address : Node with Widthable  = address_
//  var chipSelect     : Node = chipSelect_
//  var writeEnable   : Node = writeEnable_
//  var mem  : Mem[_]  = mem_
//
//
//  override def addAttribute(attribute: Attribute): this.type = addTag(attribute)
//
//
//  override def onEachInput(doThat: (Node, Int) => Unit): Unit = {
//    super.onEachInput(doThat)
//    doThat(address,MemReadWrite_readPart.getAddressId)
//    doThat(chipSelect,MemReadWrite_readPart.getChipSelectId)
//    doThat(writeEnable,MemReadWrite_readPart.getWriteEnableId)
//    doThat(mem,MemReadWrite_readPart.getMemId)
//  }
//
//  override def onEachInput(doThat: (Node) => Unit): Unit = {
//    super.onEachInput(doThat)
//    doThat(address)
//    doThat(chipSelect)
//    doThat(writeEnable)
//    doThat(mem)
//  }
//
//  override def setInput(id: Int, node: Node): Unit = id match{
//    case MemReadWrite_readPart.getAddressId => address = node.asInstanceOf[Node with Widthable]
//    case MemReadWrite_readPart.getChipSelectId => chipSelect = node
//    case MemReadWrite_readPart.getWriteEnableId => writeEnable = node
//    case MemReadWrite_readPart.getMemId => mem = node.asInstanceOf[Mem[_]]
//    case _ => super.setInput(id,node)
//  }
//
//  override def getInputsCount: Int = super.getInputsCount + 4
//  override def getInputs: Iterator[Node] = super.getInputs ++ Iterator(address,chipSelect,writeEnable,mem)
//  override def getInput(id: Int): Node = id match{
//    case MemReadWrite_readPart.getAddressId => address
//    case MemReadWrite_readPart.getChipSelectId => chipSelect
//    case MemReadWrite_readPart.getWriteEnableId => writeEnable
//    case MemReadWrite_readPart.getMemId => mem
//    case _ => super.getInput(id)
//  }
//
//
//
//  var writePart: MemReadWrite_writePart = null
//
//  override def getSynchronousInputs: List[Node] = getMem :: getAddress :: getChipSelect :: getWriteEnable :: super.getSynchronousInputs
//
//  override def isUsingResetSignal: Boolean = false
//  override def isUsingSoftResetSignal: Boolean = false
//
//  def getData = data_
//
//  def getMem = mem
//  def getAddress = address.asInstanceOf[UInt]
//  def getChipSelect = chipSelect.asInstanceOf[Bool]
//  def getWriteEnable = writeEnable.asInstanceOf[Bool]
//
//  override def calcWidth: Int = writePart.getWidth
//
//
//  override private[core] def checkInferedWidth: Unit = {
//    if(mem.getWidth != getWidth){
//      if(!hasTag(AllowMixedWidth)) {
//        PendingError(s"Read data width (${getData.getWidth} bits) is not the same than the memory one ($mem) at\n${this.getScalaLocationLong}")
//        return
//      }
//      if(mem.getWidth / getWidth * getWidth != mem.getWidth) {
//        PendingError(s"The aspect ration between written data and the memory should be a power of two. currently it's ${mem.getWidth}/${getWidth}. Memory : $mem, read at\n${this.getScalaLocationLong}")
//        return
//      }
//    }
//
//
//    if(address.getWidth != mem.addressWidth + log2Up(aspectRatio)) {
//      PendingError(s"Address used to read $mem doesn't match the required width, ${address.getWidth} bits in place of ${mem.addressWidth + log2Up(aspectRatio)} bits\n${this.getScalaLocationLong}")
//      return
//    }
//  }
//
//  def aspectRatio = mem.getWidth/getWidth
//}
//
//
