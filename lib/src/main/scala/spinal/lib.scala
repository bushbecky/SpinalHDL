package spinal

import spinal.core._

package object lib  {
  //  def Stream[T <: Data](that : T) : Stream[T] = new Stream[T](that)
  //  def mm [T <: Data with IMasterSlave](that : T) = that.asMaster()
  type Event = Stream[NoData]


  def Event = new Stream(NoData)

  def NoData = new NoData


  //implicit def easyStream[T <: Bundle](that: Stream[T]) = that.data
  implicit def traversableOncePimped[T <: Data](that: scala.collection.Iterable[T]) = new TraversableOncePimped[T](that)
  implicit def traversableOnceBoolPimped(that: scala.collection.Iterable[Bool]) = new TraversableOnceBoolPimped(that)
  implicit def traversableOnceAnyPimped[T <: Any](that: scala.collection.Iterable[T]) = new TraversableOnceAnyPimped(that)



//  implicit def seqPimped[T <: Data](that: scala.IndexedSeq[T]) = new TraversableOncePimped[T](that)

  implicit def flowFragmentPimped[T <: Data](that: Flow[Fragment[T]]) = new FlowFragmentPimped[T](that)

  implicit def streamFragmentPimped[T <: Data](that: Stream[Fragment[T]]) = new StreamFragmentPimped[T](that)

  implicit def streamBitsPimped[T <: Data](that: Stream[Bits]) = new StreamBitsPimped(that)

  implicit def flowBitsPimped[T <: Data](that: Flow[Bits]) = new FlowBitsPimped(that)


  implicit def dataCarrierFragmentPimped[T <: Data](that: DataCarrier[Fragment[T]]) = new DataCarrierFragmentPimped[T](that)

  implicit def dataCarrierFragmentBitsPimped(that: DataCarrier[Fragment[Bits]]) = new DataCarrierFragmentBitsPimped(that)

  implicit def streamFragmentBitsPimped(that: Stream[Fragment[Bits]]) = new StreamFragmentBitsPimped(that)

  implicit def stringPimped(that: String) = new StringPimped(that)


  implicit def memPimped[T <: Data](mem: Mem[T]) = new MemPimped(mem)

  implicit def boolPimped(that: Bool) = new BoolPimped(that)

  implicit class UIntPimper(that : UInt){
    def toOneHot : Bits = B"1" << that
  }

  
  def StreamArbiterFactory = new StreamArbiterFactory()

  type ScalaStream[T] = collection.immutable.Stream[T]

  def ScalaStream = collection.immutable.Stream
}