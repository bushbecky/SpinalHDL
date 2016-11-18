/*                                                                           *\
**        _____ ____  _____   _____    __                                    **
**       / ___// __ \/  _/ | / /   |  / /   HDL Core                         **
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
package spinal.core

import spinal.core.Operator.BitVector.AllByBool


/**
  * SInt factory used for instance by the IODirection to create a in/out SInt
  */
trait SIntFactory{
  /** Create a new SInt */
  def SInt() = new SInt()
  /** Create a new SInt of a given width */
  def SInt(width: BitCount): SInt = SInt.setWidth(width.value)
}


/**
  * The SInt type corresponds to a vector of bits that can be used for signed integer arithmetic.
  *
  * @see  [[http://spinalhdl.github.io/SpinalDoc/spinal/core/types/Int "SInt Documentation"]]
  */
class SInt extends BitVector with Num[SInt] with MinMaxProvider with DataPrimitives[SInt] with BitwiseOp[SInt] {

  private[core] override def prefix : String = "s"

  override type T = SInt

  private[spinal] override  def _data: SInt = this


  def @@(that: SInt): SInt = S(this ## that)
  def @@(that: UInt): SInt = S(this ## that)
  def @@(that: Bool): SInt = S(this ## that)

  override def +(right: SInt): SInt = wrapBinaryOperator(right, new Operator.SInt.Add)
  override def -(right: SInt): SInt = wrapBinaryOperator(right, new Operator.SInt.Sub)
  override def *(right: SInt): SInt = wrapBinaryOperator(right, new Operator.SInt.Mul)
  override def /(right: SInt): SInt = wrapBinaryOperator(right, new Operator.SInt.Div)
  override def %(right: SInt): SInt = wrapBinaryOperator(right, new Operator.SInt.Mod)

  def abs: UInt = Mux(this.msb,~this,this).asUInt + this.msb.asUInt
  def abs(enable: Bool): UInt = Mux(this.msb && enable, ~this, this).asUInt + (this.msb && enable).asUInt

  override def |(right: SInt): SInt = wrapBinaryOperator(right, new Operator.SInt.Or)
  override def &(right: SInt): SInt = wrapBinaryOperator(right, new Operator.SInt.And)
  override def ^(right: SInt): SInt = wrapBinaryOperator(right, new Operator.SInt.Xor)
  override def unary_~(): SInt = wrapUnaryOperator(new Operator.SInt.Not)
  def unary_-(): SInt = wrapUnaryOperator(new Operator.SInt.Minus)

  override def < (right: SInt): Bool = wrapLogicalOperator(right, new Operator.SInt.Smaller)
  override def > (right: SInt): Bool = right < this
  override def <=(right: SInt): Bool = wrapLogicalOperator(right, new Operator.SInt.SmallerOrEqual)
  override def >=(right: SInt): Bool = right <= this

  override def >>(that: Int): SInt = wrapConstantOperator(new Operator.SInt.ShiftRightByInt(that))
  override def <<(that: Int): SInt = wrapConstantOperator(new Operator.SInt.ShiftLeftByInt(that))
  def >>(that: UInt): SInt         = wrapBinaryOperator(that, new Operator.SInt.ShiftRightByUInt)
  def <<(that: UInt): SInt         = wrapBinaryOperator(that, new Operator.SInt.ShiftLeftByUInt)

  def |>>(that: Int): SInt  = wrapConstantOperator(new Operator.SInt.ShiftRightByIntFixedWidth(that))
  def |<<(that: Int): SInt  = wrapConstantOperator(new Operator.SInt.ShiftLeftByIntFixedWidth(that))
  def |>>(that: UInt): SInt = this >> that
  def |<<(that: UInt): SInt = wrapBinaryOperator(that, new Operator.SInt.ShiftLeftByUIntFixedWidth)

  override def rotateLeft(that: Int): SInt = {
    val width = widthOf(this)
    val thatMod = that % width
    this(this.high - thatMod downto 0) @@ this(this.high downto this.high - thatMod + 1)
  }

  override def rotateRight(that: Int): SInt = {
    val width = widthOf(this)
    val thatMod = that % width
    this(thatMod - 1 downto 0) @@ this(this.high downto thatMod)
  }

  def :=(rangesValue : Tuple2[Any, Any],_rangesValues: Tuple2[Any, Any]*) : Unit = {
    val rangesValues = rangesValue +: _rangesValues
    S.applyTuples(this, rangesValues)
  }

  private[core] override def newMultiplexer(sel: Bool, whenTrue: Node, whenFalse: Node): Multiplexer = newMultiplexer(sel, whenTrue, whenFalse, new MultiplexerSInt)
  private[core] override def isEquals(that: Any): Bool = {
    that match {
      case that: SInt           =>  wrapLogicalOperator(that,new Operator.SInt.Equal)
      case that: MaskedLiteral  => that === this
      case _                    => SpinalError(s"Don't know how compare $this with $that"); null
    }
  }
  private[core] override def isNotEquals(that: Any): Bool = {
    that match {
      case that: SInt          =>  wrapLogicalOperator(that,new Operator.SInt.NotEqual)
      case that: MaskedLiteral => that =/= this
      case _                   => SpinalError(s"Don't know how compare $this with $that"); null
    }
  }

  override def asBits: Bits = wrapCast(Bits(), new CastSIntToBits)
  override def assignFromBits(bits: Bits): Unit = this := bits.asSInt
  override def assignFromBits(bits: Bits, hi: Int, lo: Int): Unit = this(hi, lo).assignFromBits(bits)

  def asUInt: UInt = wrapCast(UInt(), new CastSIntToUInt)

  override def resize(width: Int): this.type = wrapWithWeakClone({
    val node = new ResizeSInt
    node.input = this
    node.size = width
    node
  })

  override def minValue: BigInt = -(BigInt(1) << (getWidth - 1))
  override def maxValue: BigInt = (BigInt(1) << (getWidth - 1)) - 1

  override def apply(bitId: Int): Bool = newExtract(bitId, new ExtractBoolFixedFromSInt)
  override def apply(bitId: UInt): Bool = newExtract(bitId, new ExtractBoolFloatingFromSInt)
  override def apply(offset: Int, bitCount: BitCount): this.type  = newExtract(offset+bitCount.value-1, offset, new ExtractBitsVectorFixedFromSInt).setWidth(bitCount.value)
  override def apply(offset: UInt, bitCount: BitCount): this.type = newExtract(offset, bitCount.value, new ExtractBitsVectorFloatingFromSInt).setWidth(bitCount.value)

  private[core] override def weakClone: this.type = new SInt().asInstanceOf[this.type]
  override def getZero: this.type = S(0, this.getWidth bits).asInstanceOf[this.type]
  override def getZeroUnconstrained: this.type = S(0).asInstanceOf[this.type]
  override protected def getAllToBoolNode(): AllByBool = new Operator.SInt.AllByBool(this)
}
