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

package spinal




import scala.collection.mutable.ArrayBuffer

/**
 * Created by PIC18F on 02.02.2015.
 */
trait MemWriteToReadKind {

}


object writeFirst extends MemWriteToReadKind {
  override def toString: String = "writeFirst"
}

object readFirst extends MemWriteToReadKind {
  override def toString: String = "readFirst"
}

object dontCare extends MemWriteToReadKind {
  override def toString: String = "dontCare"
}

object Mem {
  def apply[T <: Data](wordType: T, wordCount: Int) = new Mem(wordType, wordCount)
}

class Mem[T <: Data](val wordType: T, val wordCount: Int) extends Node with Nameable {
  var forceMemToBlackboxTranslation = false

  override def calcWidth: Int = wordType.flatten.map(_._2.calcWidth).reduceLeft(_ + _)
  def addressWidth = log2Up(wordCount)

  def setAsBlackBox : this.type = {
    forceMemToBlackboxTranslation = true
    this
  }

  def apply(address: UInt): T = {
    val ret = readAsync(address)
    ret.
    ret
  }

  def readAsync(address: UInt,writeToReadKind: MemWriteToReadKind = dontCare): T = {
    val readBits = Bits(wordType.getBitsWidth bit)
    val readWord = wordType.clone()

    val readPort = new MemReadAsync(this, address.dontSimplifyIt, readBits,writeToReadKind)
    readPort.compositeTagReady = readWord

    readBits.inputs(0) = readPort
    readWord.fromBits(readBits)

    readWord
  }

  def readSync(address: UInt, enable: Bool = Bool(true),writeToReadKind: MemWriteToReadKind = dontCare): T = {
    val readBits = Bits(wordType.getBitsWidth bit)
    val readWord = wordType.clone()

    val readPort = new MemReadSync(this, address.dontSimplifyIt, readBits, enable.dontSimplifyIt,writeToReadKind,ClockDomain.current)
    readPort.compositeTagReady = readWord

    readBits.inputs(0) = readPort
    readWord.fromBits(readBits)

    readWord
  }


  def write(address: UInt, data: T): Unit = {

    val writePort = new MemWrite(this, address.dontSimplifyIt, data.toBits.dontSimplifyIt, when.getWhensCond(this).dontSimplifyIt,ClockDomain.current)
    inputs += writePort
  }
}

class MemReadAsync(mem: Mem[_], address: UInt, data: Bits,val writeToReadKind: MemWriteToReadKind) extends Node {
  if(writeToReadKind == readFirst) SpinalError("readFirst mode for asyncronous read is not alowed")

  inputs += address
  inputs += mem

  def getData = data
  def getAddress = inputs(0).asInstanceOf[UInt]
  def getMem = inputs(1).asInstanceOf[Mem[_]]

  override def calcWidth: Int = getMem.getWidth
}


object MemReadSync {
  def getAddressId: Int = 3
  def getEnableId: Int = 4
}

class MemReadSync(mem: Mem[_], address: UInt, data: Bits, enable: Bool,val writeToReadKind: MemWriteToReadKind, clockDomain: ClockDomain) extends SyncNode(clockDomain) {
  inputs += address
  inputs += enable
  inputs += mem

  override def getSynchronousInputs: ArrayBuffer[Node] = super.getSynchronousInputs ++= getMem :: getAddress :: getEnable ::  Nil
  override def isUsingReset: Boolean = false

  def getData = data
  def getMem = mem
  def getAddress = inputs(MemReadSync.getAddressId).asInstanceOf[UInt]
  def getEnable = inputs(MemReadSync.getEnableId).asInstanceOf[Bool]

  override def calcWidth: Int = getMem.calcWidth

  def useReadEnable : Boolean = {
    val lit = getEnable.getLiteral[BoolLiteral]
    return lit == null || lit.value == false
  }

  override def normalizeInputs: Unit = {
    Misc.normalizeResize(this, MemReadSync.getAddressId, getMem.addressWidth)
  }

}


object MemWrite {
  def getAddressId: Int = 3
  def getDataId: Int = 4
  def getEnableId: Int = 5
}

class MemWrite(mem: Mem[_], address: UInt, data: Bits, enable: Bool, clockDomain: ClockDomain) extends SyncNode(clockDomain) {
  inputs += address
  inputs += data
  inputs += enable


  override def getSynchronousInputs: ArrayBuffer[Node] = super.getSynchronousInputs ++= getAddress :: getData :: getEnable :: Nil
  override def isUsingReset: Boolean = false

  def getMem = mem
  def getAddress = inputs(MemWrite.getAddressId).asInstanceOf[UInt]
  def getData = inputs(MemWrite.getDataId).asInstanceOf[Bits]
  def getEnable = inputs(MemWrite.getEnableId).asInstanceOf[Bool]

  override def calcWidth: Int = getMem.calcWidth

  def useWriteEnable : Boolean = {
    val lit = getEnable.getLiteral[BoolLiteral]
    return lit == null || lit.value == false
  }

  override def normalizeInputs: Unit = {
    Misc.normalizeResize(this, MemReadSync.getAddressId, getMem.addressWidth)
  }

}

class MemReadWrite extends Node {
  override def calcWidth: Int = ???
}



class Ram_1c_1w_1ra(wordWidth: Int, wordCount: Int, writeToReadKind: MemWriteToReadKind = dontCare) extends BlackBox {
  if(writeToReadKind == readFirst) SpinalError("readFirst mode for asyncronous read is not alowed")

  val generic = new Generic {
    val wordCount = Ram_1c_1w_1ra.this.wordCount
    val wordWidth = Ram_1c_1w_1ra.this.wordWidth
    val readToWriteKind = writeToReadKind.toString
  }

  val io = new Bundle {
    val clk = in Bool()
    val clkEn = in Bool()

    val wr = new Bundle {
      val en = in Bool()
      val addr = in UInt (log2Up(wordCount) bit)
      val data = in Bits (wordWidth bit)
    }
    val rd = new Bundle {
      val addr = in UInt (log2Up(wordCount) bit)
      val data = out Bits (wordWidth bit)
    }
  }

  useCurrentClockDomain(io.clk,null,io.clkEn)

  //Following is not obligatory, just to describe blackbox logic
  val mem = Mem(io.wr.data, wordCount)
  when(io.wr.en) {
    mem.write(io.wr.addr, io.wr.data)
  }
  io.rd.data := mem.readAsync(io.rd.addr)
}

class Ram_1c_1w_1rs(wordWidth: Int, wordCount: Int, writeToReadKind: MemWriteToReadKind = dontCare) extends BlackBox {

  val generic = new Generic {
    val wordCount = Ram_1c_1w_1rs.this.wordCount
    val wordWidth = Ram_1c_1w_1rs.this.wordWidth
    val readToWriteKind = writeToReadKind.toString
    var useReadEnable = true
  }

  val io = new Bundle {
    val clk = in Bool()
    val clkEn = in Bool()

    val wr = new Bundle {
      val en = in Bool()
      val addr = in UInt (log2Up(wordCount) bit)
      val data = in Bits (wordWidth bit)
    }
    val rd = new Bundle {
      val en = in Bool()
      val addr = in UInt (log2Up(wordCount) bit)
      val data = out Bits (wordWidth bit)
    }
  }

  useCurrentClockDomain(io.clk,null,io.clkEn)

  def useReadEnable = io.rd.en.getLiteral[BoolLiteral]

  //Following is not obligatory, just to describe blackbox logic
  val mem = Mem(io.wr.data, wordCount)
  when(io.wr.en) {
    mem.write(io.wr.addr, io.wr.data)
  }
  io.rd.data := mem.readSync(io.rd.addr, io.rd.en)
}
