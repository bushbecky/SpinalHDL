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


object OutBinding  {
  def apply(out: BaseType, into: Component /*,from : Component*/): OutBinding = {
    var bind = into.findBinding(out)
    if (bind != null) return bind
    bind = new OutBinding(out)
    into.outBindingHosted.put(out,bind)
    bind.inputs += out
    bind.component = into
    bind
  }
}


class OutBinding(val out: BaseType) extends Node with Nameable {
  override def calcWidth : Int = WidthInfer.inputMaxWidthl(this)
}
