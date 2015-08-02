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


object Component {
  def apply[T <: Component](c: T): T = {
    //    c.io.flatten.foreach(_._2.isIo = true)
    //    pop(c);
    //    c.userParentCalledDef
    return c;
  }


  def push(c: Component): Unit = {
    //  if (when.stack.size() != 0) throw new Exception("Creating a component into hardware conditional expression")
    GlobalData.get.componentStack.push(c)
  }

  def pop(c: Component): Unit = {
    try {
      /* if(lastPoped == c) return;
       lastPoped = c*/
      GlobalData.get.componentStack.pop(c)
    } catch {
      case e: Exception => SpinalError(s"You probably forget the 'Component(new ${c.globalData.componentStack.head().getClass.getName})' into ${c.getClass.getName}")
    }
  }

  //var lastPoped : Component = null

  def current: Component = current(GlobalData.get)

  def current(globalData: GlobalData): Component = globalData.componentStack.head()
}


abstract class Component extends NameableByComponent with GlobalDataUser with ScalaLocated with DelayedInit {

  override def delayedInit(body: => Unit) = {
    body

    if ((body _).getClass.getDeclaringClass == this.getClass) {
      // this.io.flatten.foreach(_.isIo = true)
      Component.pop(this);
      this.userParentCalledDef
    }
  }


  //def io: Data
  val ioSet = mutable.Set[BaseType]()

  val userCache = mutable.Map[Object, mutable.Map[Object, Object]]()
  val localScope = new Scope()
  val postCreationTask = mutable.ArrayBuffer[() => Unit]()
  val kindsOutputsToBindings = mutable.Map[BaseType, BaseType]()
  val kindsOutputsBindings = mutable.Set[BaseType]()
  val additionalNodesRoot = mutable.Set[BaseType]()
  var definitionName: String = null
  val level = globalData.componentStack.size()
  val kinds = ArrayBuffer[Component]()
  val parent = Component.current
  if (parent != null) {
    parent.kinds += this;
  }else{
    setWeakName("toplevel")
  }

  def isTopLevel: Boolean = parent == null

  val initialWhen = globalData.whenStack.head()

  var nodes: ArrayBuffer[Node] = null


  var pulledDataCache = mutable.Map[Data, Data]()


  Component.push(this)


  def parents(of: Component = this, list: List[Component] = Nil): List[Component] = {
    if (of.parent == null) return list
    parents(of.parent, of.parent :: list)
  }

  def reflectIo: Data = {
    try {
      val clazz = this.getClass
      val m = clazz.getMethod("io")
      m.invoke(this).asInstanceOf[Data]
    } catch {
      case _: Throwable => null
    }
  }

  def nameElements(): Unit = {
    //TODO this.io.setWeakName("io") //Because Misc.reflect don't keep the declaration order
    Misc.reflect(this, (name, obj) => {
      obj match {
        case component: Component => {
          if (component.parent == this)
            component.setWeakName(name)
        }
        case namable: Nameable => {
          if (!namable.isInstanceOf[ContextUser])
            namable.setWeakName(name)
          else if (namable.asInstanceOf[ContextUser].component == this)
            namable.setWeakName(name)
          else {
            for (kind <- kinds) {
              //Allow to name a component by his io reference into the parent component
              if (kind.reflectIo == namable) {
                kind.setWeakName(name)
              }
            }
          }
        }
        case _ =>
      }
    })
  }

  def allocateNames(): Unit = {
    localScope.allocateName("zz")
    for (node <- nodes) node match {
      case nameable: Nameable => {
        if (nameable.isUnnamed) {
          nameable.setWeakName("zz")
        }
        if (nameable.isWeak)
          nameable.setName(localScope.allocateName(nameable.getName()));
        else
          localScope.iWantIt(nameable.getName())
      }
      case _ =>
    }
    for (kind <- kinds) {
      if (kind.isUnnamed) {
        var name = kind.getClass.getSimpleName
        name = Character.toLowerCase(name.charAt(0)) + (if (name.length() > 1) name.substring(1) else "");
        kind.setWeakName(name)
      }
      kind.setName(localScope.allocateName(kind.getName()));
    }
  }


  def getNodeIo = {

    if (nodes == null) {
      ioSet
    } else {
      val nodeIo = mutable.Set[BaseType]()
      nodes.foreach(node => node match {
        case b: BaseType => if (b.isIo) nodeIo += b
        case _ =>
      })
      nodeIo
    }

  }

  def getOrdredNodeIo = getNodeIo.toList.sortWith(_.instanceCounter < _.instanceCounter)


  def getDelays = {
    val delays = new ArrayBuffer[SyncNode]()
    nodes.foreach(node => node match {
      case delay: SyncNode => delays += delay
      case _ =>
    })
    delays
  }


  def userParentCalledDef: Unit = {

  }

  def isInBlackBoxTree: Boolean = if (parent == null) false else parent.isInBlackBoxTree

  override def getComponent(): Component = parent
}


