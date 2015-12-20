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
 * Created by PIC18F on 11.01.2015.
 */



object when {


  def doWhen(w: when, isTrue: Boolean)(block: => Unit): when = {
    w.isTrue = isTrue
    w.push
    block
    w.pop
    w
  }

  def apply(cond: Bool)(block: => Unit): when = {
    doWhen(new when(cond), true)(block)
  }

  //Allow the user to get a Bool that represent the  aggregation of all condition to the current context
  def getWhensCond(that: ContextUser): Bool = {
    val whenScope = if (that == null) null else that.whenScope

    var ret: Bool = null
    for (w <- that.globalData.whenStack.stack) {
      if (w == whenScope) return returnFunc
      val newCond = (if (w.isTrue) w.cond else !w.cond)
      if (ret == null) {
        ret = newCond
      } else {
        ret = ret && newCond
      }
    }


    def returnFunc = if (ret == null) True else ret

    returnFunc
  }
}

class when(val cond: Bool) extends GlobalDataUser {
  var isTrue: Boolean = true;
  var parentElseWhen: when = null

  def otherwise(block: => Unit): Unit = {
    restackElseWhen
    when.doWhen(this, false)(block)
    destackElseWhen
  }

  def elsewhen(cond: Bool)(block: => Unit): when = {
    var w: when = null
    otherwise({
      w = when(cond) {
        block
      }
      w.parentElseWhen = this
    })
    w
  }

  def restackElseWhen: Unit = {
    if (parentElseWhen == null) return
    parentElseWhen.restackElseWhen
    parentElseWhen.push
  }

  def destackElseWhen: Unit = {
    if (parentElseWhen == null) return
    parentElseWhen.pop
    parentElseWhen.destackElseWhen
  }


  def push: Unit = {
    globalData.whenStack.push(this)
  }

  def pop: Unit = {
    globalData.whenStack.pop(this)
  }

}

class SwitchStack(val value: Data) {
  var lastWhen: when = null
  var defaultBlock: () => Unit = null
  val whenStackHead = GlobalData.get.whenStack.head()
}

object switch {

  def apply[T <: Data](value: T)(block: => Unit): Unit = {
    val s = new SwitchStack(value)
    value.globalData.switchStack.push(s)
    block
    if (s.defaultBlock != null) {
      if (s.lastWhen == null) {
        block
      } else {
        s.lastWhen.otherwise(s.defaultBlock())
      }
    }
    value.globalData.switchStack.pop(s)
  }
}


object is {
  def apply(value: Any*)(block: => Unit): Unit = {
    val e = ArrayBuffer[Data]()

    def analyse(key: Any): Unit = {
      key match {
        case that: Data => e.+=(that)
        case that: Seq[_] => that.foreach(analyse(_))
        case that: Int => {
          val globalData = GlobalData.get
          if (globalData.switchStack.isEmpty) SpinalError("Use 'is' statement outside the 'switch'")
          val value = globalData.switchStack.head().value
          value match {
            case x: Bits => e += B(that)
            case x: UInt => e += U(that)
            case x: SInt => e += S(that)
            case _ => SpinalError("The switch is not a Bits, UInt or SInt")
          }
        }
        case that : SpinalEnumElement[_] => e += that()
      }
    }

    for (v <- value) {
      analyse(v)
    }
    impl(e)(block)
  }

  def impl(keys: Iterable[Data])(block: => Unit): Unit = {
    if (keys.isEmpty) SpinalError("There is no key in 'is' statement")
    val globalData = keys.head.globalData
    if (globalData.switchStack.isEmpty) SpinalError("Use 'is' statement outside the 'switch'")

    val value = globalData.switchStack.head()
    if (value.whenStackHead != globalData.whenStack.head())
      SpinalError("'is' statement is not at the top level of the 'switch'")
    //    if(value.defaultBlock != null)
    //      SpinalError("'is' statement must appear before the 'default' statement of the 'switch'")

    if (value.lastWhen == null) {
      value.lastWhen = when(keys.map(key => (key.isEguals(value.value))).reduceLeft(_ || _))(block)
    } else {
      value.lastWhen = value.lastWhen.elsewhen(keys.map(key => (key.isEguals(value.value))).reduceLeft(_ || _))(block)
    }
  }
}

object default {
  def apply(block: => Unit): Unit = {
    val globalData = GlobalData.get
    if (globalData.switchStack.isEmpty) SpinalError("Use 'default' statement outside the 'switch'")
    val value = globalData.switchStack.head()

    if (value.whenStackHead != globalData.whenStack.head()) SpinalError("'default' statement is not at the top level of the 'switch'")
    if (value.defaultBlock != null) SpinalError("'default' statement must appear only one time in the 'switch'")
    value.defaultBlock = () => {
      block
    }
  }
}

object WhenNode {

  def apply(w: when): WhenNode = {
    apply(w, w.cond, NoneNode(), NoneNode())
  }

  def apply(w: when, cond: Bool, whenTrue: Node, whenFalse: Node): WhenNode = {
    val ret = new WhenNode(w)
    ret.inputs += cond
    ret.inputs += whenTrue
    ret.inputs += whenFalse
    ret
  }
}

class WhenNode(val w: when) extends Node {
  override def calcWidth: Int = Math.max(whenTrue.getWidth, whenFalse.getWidth)

  def cond = inputs(0)

  def whenTrue = inputs(1)

  def whenFalse = inputs(2)

  override def normalizeInputs: Unit = {
    Misc.normalizeResize(this, 1, this.getWidth)
    Misc.normalizeResize(this, 2, this.getWidth)
  }
}



object switch2 {

  def apply[T <: Data](value: T)(block: => Unit): Unit = {
    val s = new SwitchStack(value)
    value.globalData.switchStack.push(s)
    block
    if (s.defaultBlock != null) {
      if (s.lastWhen == null) {
        block
      } else {
        s.lastWhen.otherwise(s.defaultBlock())
      }
    }
    value.globalData.switchStack.pop(s)
  }
}

class Switch2[T <: BaseType](value : T){
  val keys = new ArrayBuffer[T]
}

class Switch2Node() extends Node{
  def cond = inputs(0)
  def values = inputs.iterator.drop(1)

  override def normalizeInputs: Unit = {
    for((input,i)  <- inputs.zipWithIndex){
      Misc.normalizeResize(this, i, this.getWidth)
    }
  }

  override def calcWidth: Int = values.foldLeft(-1)((a,b) => Math.max(a,b.getWidth))
}