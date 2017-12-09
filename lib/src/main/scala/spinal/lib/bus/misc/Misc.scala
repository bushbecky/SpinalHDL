/*                                                                           *\
**        _____ ____  _____   _____    __                                    **
**       / ___// __ \/  _/ | / /   |  / /   HDL Lib                          **
**       \__ \/ /_/ // //  |/ / /| | / /    (c) Dolu, All rights reserved    **
**      ___/ / ____// // /|  / ___ |/ /___                                   **
**     /____/_/   /___/_/ |_/_/  |_/_____/                                   **
**                                                                           **
**      This library is free software; you can redistribute it and/or        **
**    modify it under the terms of the GNU Lesser General Public             **
**    License as published by the Free Software Foundation; either           **
**    version 3.0 of the License, or (at your option) any later version.     **
**                                                                           **
**      This library is distributed in the hope that it will be useful,      **
**    but WITHOUT ANY WARRANTY; without even the implied warranty of         **
**    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU      **
**    Lesser General Public License for more details.                        **
**                                                                           **
**      You should have received a copy of the GNU Lesser General Public     **
**    License along with this library.                                       **
\*                                                                           */
package spinal.lib.bus.misc

import spinal.core._


trait AddressMapping{
  def hit(address: UInt): Bool
  def removeOffset(address: UInt): UInt
  def lowerBound : BigInt
  def applyOffset(addressOffset: BigInt): AddressMapping
}


case class SingleMapping(address : BigInt) extends AddressMapping{
  override def hit(address: UInt) = this.address === address
  override def removeOffset(address: UInt) = U(0)
  override def lowerBound = address
  override def applyOffset(addressOffset: BigInt): AddressMapping = SingleMapping(address + addressOffset)
  override def toString: String = s"Address 0x${address.toString(16)}"
}


case class MaskMapping(base : BigInt,mask : BigInt) extends AddressMapping{
  override def hit(address: UInt): Bool = (address & base) === mask
  override def removeOffset(address: UInt) = address & ~mask
  override def lowerBound = base
  override def applyOffset(addressOffset: BigInt): AddressMapping = ???
}


object SizeMapping{
  implicit def implicitTuple1(that: (Int, Int))      : SizeMapping = SizeMapping(that._1, that._2)
  implicit def implicitTuple2(that: (BigInt, BigInt)): SizeMapping = SizeMapping(that._1, that._2)
  implicit def implicitTuple3(that: (Int, BigInt))   : SizeMapping = SizeMapping(that._1, that._2)
  implicit def implicitTuple5(that: (Long, BigInt))  : SizeMapping = SizeMapping(that._1, that._2)
  implicit def implicitTuple4(that: (BigInt, Int))   : SizeMapping = SizeMapping(that._1, that._2)
}


case class SizeMapping(base: BigInt, size: BigInt) extends AddressMapping {
  override def hit(address: UInt): Bool = if (isPow2(size) && base % size == 0)
    (address & S(-size, address.getWidth bits).asUInt) === (base)
  else
    address >= base && address < base + size

  override def removeOffset(address: UInt): UInt = {
    if (isPow2(size) && base % size == 0)
      address & (size - 1)
    else
      address - base
  }.resize(log2Up(size))

  override def lowerBound = base
  override def applyOffset(addressOffset: BigInt): AddressMapping = SizeMapping(base + addressOffset, size)
}