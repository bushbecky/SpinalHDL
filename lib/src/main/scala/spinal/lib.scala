package spinal

import spinal.core._

package object lib {
  //  def Handshake[T <: Data](that : T) : Handshake[T] = new Handshake[T](that)
//  def mm [T <: Data with IMasterSlave](that : T) = that.asMaster
  type Event = Handshake[NoData]

  def Event = new Handshake(NoData)
  def NoData = new NoData

  implicit def traversableOncePimped[T](that: TraversableOnce[T]) = new TraversableOncePimped[T](that)

  implicit def flowFragmentPimped[T <: Data](that: Flow[Fragment[T]]) = new FlowFragmentPimped[T](that)
  implicit def handshakeFragmentPimped[T <: Data](that: Handshake[Fragment[T]]) = new HandshakeFragmentPimped[T](that)

  implicit def handshakeBitsPimped[T <: Data](that: Handshake[Bits]) = new HandshakeBitsPimped(that)
  implicit def flowBitsPimped[T <: Data](that: Flow[Bits]) = new FlowBitsPimped(that)


  implicit def dataCarrierFragmentPimped[T <: Data](that: DataCarrier[Fragment[T]]) = new DataCarrierFragmentPimped[T](that)
  implicit def dataCarrierFragmentBitsPimped(that: DataCarrier[Fragment[Bits]]) = new DataCarrierFragmentBitsPimped(that)

  implicit def handshakeFragmentBitsPimped(that: Handshake[Fragment[Bits]]) = new HandshakeFragmentBitsPimped(that)


  implicit def memPimped[T <: Data](mem: Mem[T]) = new MemPimped(mem)


  def HandshakeArbiter = new HandshakeArbiterCoreFactory()

}