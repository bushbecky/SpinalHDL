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

import scala.collection.mutable.ArrayBuffer


/**
  * Base class for creating enumeration
  *
  * @see  [[http://spinalhdl.github.io/SpinalDoc/spinal/core/types/Enum "Enumeration Documentation"]]
  *
  * @example {{{
  *         class MyEnum extends SpinalEnum(binarySequancial){
  *           val s1, s2, s3, s4 = newElement()
  *         }
  *         }}}
  *
  * SpinalEnum contains a list of SpinalEnumElement that is the definition of an element. SpinalEnumCraft is the
  * hardware representation of the the element.
  *
  * @param defaultEncoding encoding of the enum
  */
class SpinalEnum(var defaultEncoding: SpinalEnumEncoding = native) extends Nameable {

  assert(defaultEncoding != inferred, "Enum definition should not have 'inferred' as default encoding")

  type C = SpinalEnumCraft[this.type]
  type E = SpinalEnumElement[this.type]

  /** Contains all elments of the enumeration */
  val values = ArrayBuffer[SpinalEnumElement[this.type]]()


  /** Return the coresponding craft */
  def apply() = craft()
  /** Return the corresponding craft with a given encoding */
  def apply(encoding: SpinalEnumEncoding) = craft(encoding)


  /** Return the corresponding craft */
  def craft(): SpinalEnumCraft[this.type] = craft(defaultEncoding)

  /** Return the corresponding craft with a given encoding */
  def craft(enumEncoding: SpinalEnumEncoding): SpinalEnumCraft[this.type] = {
    val ret = new SpinalEnumCraft[this.type](this)
    if(enumEncoding != `inferred`) ret.fixEncoding(enumEncoding)
    ret
  }


  /** Create a new Element */
  def newElement(): SpinalEnumElement[this.type] = newElement(null)

  /** Create a new Element with a name */
  def newElement(name: String): SpinalEnumElement[this.type] = {
    val v = new SpinalEnumElement(this,values.size).asInstanceOf[SpinalEnumElement[this.type]]
    if (name != null) v.setName(name)
    values += v
    v
  }
}


/**
  * Definition of an element of the enumeration
  */
class SpinalEnumElement[T <: SpinalEnum](val parent: T, val position: Int) extends Nameable {


  def ===(that: SpinalEnumCraft[T]): Bool = that === this

  def =/=(that: SpinalEnumCraft[T]): Bool = that =/= this

  def apply(): SpinalEnumCraft[T] = craft()
  def apply(encoding: SpinalEnumEncoding): SpinalEnumCraft[T] = craft(encoding)

  def craft(): SpinalEnumCraft[T] = {
    val ret = parent.craft(inferred).asInstanceOf[SpinalEnumCraft[T]]
    ret.input = new EnumLiteral(this)
    ret
  }

  def craft(encoding: SpinalEnumEncoding): SpinalEnumCraft[T] = {
    val ret = parent.craft(encoding).asInstanceOf[SpinalEnumCraft[T]]
    val lit = new EnumLiteral(this)
    lit.fixEncoding(encoding)
    ret.input = lit
    ret
  }

  def asBits: Bits = craft().asBits
}


/**
  * Hardware representation of an enumeration
  */
class SpinalEnumCraft[T <: SpinalEnum](val blueprint: T/*, encoding: SpinalEnumEncoding*/) extends BaseType with InferableEnumEncodingImpl with DataPrimitives[SpinalEnumCraft[T]] {

  private[core] override def getDefaultEncoding(): SpinalEnumEncoding = blueprint.defaultEncoding

  override def getDefinition: SpinalEnum = blueprint

  private[spinal] override def _data: SpinalEnumCraft[T] = this

  private[core] def assertSameType(than: SpinalEnumCraft[_]): Unit = if (blueprint != than.blueprint) SpinalError("Enum is assigned by a incompatible enum")

  def :=(that: SpinalEnumElement[T]): Unit = new DataPimper(this) := that.craft()
  def ===(that: SpinalEnumElement[T]): Bool = this === (that.craft())
  def =/=(that: SpinalEnumElement[T]): Bool = this =/= (that.craft())

  @deprecated("Use =/= instead")
  def !==(that: SpinalEnumElement[T]): Bool = this =/= that


  private[core] override def assignFromImpl(that: AnyRef, conservative: Boolean): Unit = that match{
    case that : SpinalEnumCraft[T] => {
      super.assignFromImpl(that, conservative)
    }
  }

  override def isEquals(that: Any): Bool = {
    that match{
      case that: SpinalEnumCraft[_] if that.blueprint == blueprint => wrapLogicalOperator(that, new Operator.Enum.Equal(blueprint));
      case that: SpinalEnumElement[_] if that.parent == blueprint  => wrapLogicalOperator(that(), new Operator.Enum.Equal(blueprint));
      case _                                                       => SpinalError("Incompatible test")
    }
  }
  override def isNotEquals(that: Any): Bool = {
    that match{
      case that: SpinalEnumCraft[_] if that.blueprint == blueprint => wrapLogicalOperator(that, new Operator.Enum.NotEqual(blueprint));
      case that: SpinalEnumElement[_] if that.parent == blueprint  => wrapLogicalOperator(that(), new Operator.Enum.NotEqual(blueprint));
      case _                                                       => SpinalError("Incompatible test")
    }
  }

  private[core] override def newMultiplexer(sel: Bool, whenTrue: Node, whenFalse: Node): Multiplexer = newMultiplexer(sel, whenTrue, whenFalse, new MultiplexerEnum(blueprint))

  override def asBits: Bits = wrapCast(Bits(), new CastEnumToBits)

  override def assignFromBits(bits: Bits): Unit = {
    val c = cloneOf(this)
    val cast = new CastBitsToEnum(this.blueprint)
    cast.input = bits.asInstanceOf[cast.T]
    c.input = cast
    this := c
  }

  override def assignFromBits(bits: Bits, hi: Int, lo: Int): Unit = {
    assert(lo == 0, "Enumeration can't be partially assigned")
    assert(hi == getBitsWidth-1, "Enumeration can't be partially assigned")
    assignFromBits(bits)
  }

  override def getBitsWidth: Int = encoding.getWidth(blueprint)

  override def clone: this.type = {
    val res = new SpinalEnumCraft(blueprint).asInstanceOf[this.type]
    res.copyEncodingConfig(this)
    res
  }

  def init(enumElement: SpinalEnumElement[T]): this.type = {
    this.initImpl(enumElement())
  }

  /** Return the name of the parent */
  private[core] def getParentName: String = blueprint.getName()

  override def getZero: this.type = {
    val ret = clone
    ret.assignFromBits(B(0))
    ret
  }
  private[core] override def weakClone: this.type = {
    val ret = new SpinalEnumCraft(blueprint).asInstanceOf[this.type]
    ret
  }

  override private[core] def normalizeInputs: Unit = {
    InputNormalize.enumImpl(this)
  }
}


/**
  * Node representation which contains the value of an SpinalEnumElement
  */
class EnumLiteral[T <: SpinalEnum](val enum: SpinalEnumElement[T]) extends Literal with InferableEnumEncodingImpl {

  override def clone: this.type = {
    val ret = new EnumLiteral(enum).asInstanceOf[this.type]
    ret.copyEncodingConfig(this)
    ret
  }

  private[core] override def getBitsStringOn(bitCount: Int): String = {
    val str = encoding.getValue(enum).toString(2)
    return "0" * (bitCount - str.length) + str
  }

  override def getDefinition: SpinalEnum = enum.parent

  private[core] override def getDefaultEncoding(): SpinalEnumEncoding = enum.parent.defaultEncoding
}



/**
  * Trait to define an encoding
  */
trait SpinalEnumEncoding extends Nameable{
  /** Return the width of the encoding  */
  def getWidth(enum: SpinalEnum): Int
  /** Return the value of the encoding */
  def getValue[T <: SpinalEnum](element: SpinalEnumElement[T]): BigInt
  /** ???  */
  def isNative: Boolean
}


/**
  * ??
  */
object inferred extends SpinalEnumEncoding{
  override def getWidth(enum: SpinalEnum): Int = ???
  override def getValue[T <: SpinalEnum](element: SpinalEnumElement[T]): BigInt = ???
  override def isNative: Boolean = ???
}


/**
  *
  */
object native extends SpinalEnumEncoding{
  override def getWidth(enum: SpinalEnum): Int = log2Up(enum.values.length)
  override def getValue[T <: SpinalEnum](element: SpinalEnumElement[T]) : BigInt = {
    return element.position
  }
  override def isNative = true
  setWeakName("native")
}



/**
  * Binary Sequential
  * @example{{{ 000, 001, 010, 011, 100, 101, .... }}}
  */
object binarySequential extends SpinalEnumEncoding{
  override def getWidth(enum: SpinalEnum): Int = log2Up(enum.values.length)
  override def getValue[T <: SpinalEnum](element: SpinalEnumElement[T]): BigInt = {
    return element.position
  }

  override def isNative = false
  setWeakName("binary_sequancial")
}


/**
  * Binary One hot encoding
  * @example{{{ 000, 010, 100 }}}
  */
object binaryOneHot extends SpinalEnumEncoding{
  override def getWidth(enum: SpinalEnum): Int = enum.values.length
  override def getValue[T <: SpinalEnum](element: SpinalEnumElement[T]): BigInt = {
    return BigInt(1) << element.position
  }
  override def isNative = false
  setWeakName("binary_one_hot")
}


/**
  * ??
  */
object Encoding{

  def apply[X <: SpinalEnum](name: String)(spec: (SpinalEnumElement[X],BigInt)*): SpinalEnumEncoding = {
    val map: Map[SpinalEnumElement[X],BigInt] = spec.toMap
    list(name)(map)
  }

  def list[X <: SpinalEnum](name: String)(spec: Map[SpinalEnumElement[X],BigInt]): SpinalEnumEncoding = {
    if(spec.size != spec.head._1.blueprint.values.size){
      SpinalError("All elements of the enumeration should be mapped")
    }
    return new SpinalEnumEncoding {
      val width = log2Up(spec.values.foldLeft(BigInt(0))((a, b) => if(a > b) a else b) + 1)
      override def getWidth(enum: SpinalEnum): Int = width

      override def isNative: Boolean = false
      override def getValue[T <: SpinalEnum](element: SpinalEnumElement[T]) : BigInt = {
        return spec(element.asInstanceOf[SpinalEnumElement[X]])
      }
      setWeakName(name)
    }
  }
}
