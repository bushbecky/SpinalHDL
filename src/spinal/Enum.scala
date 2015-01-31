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

import scala.collection.mutable


class EnumLiteral[T <: SpinalEnum](val enum: SpinalEnumElement[T]) extends Literal {
  override def calcWidth: Int = enum.parent.getWidth
}

class SpinalEnumCraft[T <: SpinalEnum](val blueprint: T) extends BaseType {
  def :=(that: SpinalEnumCraft[T]): Unit = this.assignFromImpl(that)
  def :=(that: SpinalEnumElement[T]): Unit = this := that.craft()

  def ==(that: SpinalEnumCraft[T]): Bool = newLogicalOperator("e==e", that, InputNormalize.none);
  def ==(that: SpinalEnumElement[T]): Bool = this == (that.craft())

  def !=(that: SpinalEnumCraft[T]): Bool = newLogicalOperator("e!=e", that, InputNormalize.none);
  def !=(that: SpinalEnumElement[T]): Bool = this != (that.craft())


  override def isEguals(that: Data): Bool = {
    that match {
      case that: SpinalEnumCraft[T] => this == that
      case _ => SpinalError(s"Don't know how compare $this with $that"); null
    }
  }

  override def newMultiplexor(sel: Bool, whenTrue: Node, whenFalse: Node): Multiplexer = Multiplex("mux(B,e,e)", sel, whenTrue, whenFalse)
  override def toBits: Bits = ???
  override def calcWidth: Int = blueprint.getWidth
  override def clone: this.type = {
    val res = new SpinalEnumCraft(blueprint).asInstanceOf[this.type]
    res.dir = this.dir
    res
  }

}

class SpinalEnumElement[T <: SpinalEnum](val parent: T, val id: Int, val name: String) {
  def ==(that: SpinalEnumCraft[T]) = {
    that == this
  }
  def !=(that: SpinalEnumCraft[T]) = {
    that != this
  }


  def craft(): SpinalEnumCraft[T] = {
    val ret = parent.craft().asInstanceOf[SpinalEnumCraft[T]]
    ret.inputs(0) = new EnumLiteral(this)
    ret
  }
}

class SpinalEnum {

  private val idMap = new mutable.HashMap[Int, SpinalEnumElement[this.type]]()


  var nextInt = 0
  def getNextInt: Int = {
    val i = nextInt
    nextInt = nextInt + 1
    if (idMap.contains(nextInt)) getNextInt else i
  }

  def values = idMap.values

  def Value(): SpinalEnumElement[this.type] = Value(getNextInt, null)
  def Value(id: Int): SpinalEnumElement[this.type] = Value(id, null)
  def Value(name: String): SpinalEnumElement[this.type] = Value(getNextInt, name)
  def Value(id: Int, name: String): SpinalEnumElement[this.type] = {
    if (idMap.contains(id)) SpinalError("Spinal enumeration already contain this unique id")
    val v = new SpinalEnumElement(this, id, name).asInstanceOf[SpinalEnumElement[this.type]]
    idMap += id -> v
    v
  }

  /*
    implicit def valueToCraft(x: SpinalEnumElement[this.type]): SpinalEnumCraft[this.type] = {
      val ret = craft()
      ret.inputs(0) = new EnumLiteral(this, x.id)
      ret
    }*/

  def getWidth = log2Up(values.foldLeft(0)((v, n) => Math.max(v, n.id)) + 1)
  def craft(): SpinalEnumCraft[this.type] = new SpinalEnumCraft[this.type](this)

  //type SpinalEnum = Val

  /* class Val(i: Int, name: String) extends Val(i, name) {
     def ===(that: this.type) = "hallo3"
   }*/


}
