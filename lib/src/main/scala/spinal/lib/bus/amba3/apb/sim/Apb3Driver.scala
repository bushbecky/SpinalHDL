package spinal.lib.bus.amba3.apb.sim

import spinal.core.ClockDomain
import spinal.core.sim._
import spinal.lib.bus.amba3.apb.Apb3

case class Apb3Driver(apb : Apb3, clockDomain : ClockDomain) {
  apb.PSEL #= 0
  apb.PENABLE #= false


  def write(address : BigInt, data : BigInt) : Unit@suspendable = {
    apb.PSEL #= 1
    apb.PENABLE #= false
    apb.PWRITE #= true
    apb.PADDR #= address
    apb.PWDATA #= data
    clockDomain.waitSampling()
    apb.PENABLE #= true
    clockDomain.waitSamplingWhere(apb.PREADY.toBoolean)
    apb.PSEL #= 0
    apb.PENABLE.randomize()
    apb.PADDR.randomize()
    apb.PWDATA.randomize()
    apb.PWRITE.randomize()
  }

  def read(address : BigInt) : BigInt@suspendable = {
    apb.PSEL #= 1
    apb.PENABLE #= false
    apb.PADDR #= address
    apb.PWRITE #= false
    clockDomain.waitSampling()
    apb.PENABLE #= true
    clockDomain.waitSamplingWhere(apb.PREADY.toBoolean)
    apb.PSEL #= 0
    apb.PENABLE.randomize()
    apb.PADDR.randomize()
    apb.PWDATA.randomize()
    apb.PWRITE.randomize()
    apb.PRDATA.toBigInt
  }
}