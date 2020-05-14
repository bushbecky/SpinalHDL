package spinal.lib.bus.regif

import spinal.core._

case class Field(name: String,
                 hardbit: Bits,
                 section: Range,
                 accType: AccessType,
                 resetValue: Long,
                 readError: Boolean,
                 doc: String){

  def tailBitPos = section.max

  def accept(vs : BusIfVisitor) = {
      vs.field(name, hardbit.getWidth, accType, resetValue, doc)
  }
}