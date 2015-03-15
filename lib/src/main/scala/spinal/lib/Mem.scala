package spinal.lib

import spinal.core._


class HandshakeReadRetData[T <: Data,T2 <: Data](readType : T,linkedType : T2) extends Bundle{
  val value = cloneOf(readType)
  val linked = cloneOf(linkedType)
}

class MemPimped[T <: Data](mem : Mem[T]) {
  def handshakeReadSync[T2 <: Data](cmd: Handshake[UInt],linkedData : T2) = {
    val ret = Handshake(new HandshakeReadRetData(mem.wordType,linkedData))

    val retValid = RegInit(Bool(false))
    val retData = mem.readSync(cmd.data,cmd.ready)
    val aaa : Data = linkedData
    val retLinked = RegNextWhen(aaa,cmd.ready)

    when(ret.ready){
      retValid := Bool(false)
    }
    when(cmd.ready){
      retValid := cmd.valid
    }

    cmd.ready := ret.isFree

    ret.valid := retValid
    ret.data.value := retData
    ret.data.linked := retLinked
    ret
  }
}
