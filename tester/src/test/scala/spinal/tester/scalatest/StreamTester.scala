package spinal.tester.scalatest

import spinal.core._
import spinal.lib._


object StreamTester{
  case class BundleA(aaa : Int) extends Bundle{
    val a = UInt(8 bit)
    val b = Bool
  }
}

import spinal.tester.scalatest.StreamTester._

class StreamTester extends Component {
  val io = new Bundle {
    val slave0 = slave Stream new BundleA(8)
    val master0 = master Stream new BundleA(8)
    val fifo0_occupancy = out UInt
  }

  val fifo0 = new StreamFifo(new BundleA(8),16)
  fifo0.io.push << io.slave0
  fifo0.io.pop >/-> io.master0
  io.fifo0_occupancy := fifo0.io.occupancy

  assert(3 == LatencyAnalysis(io.slave0.a,io.master0.a))
  assert(2 == LatencyAnalysis(io.master0.ready,io.slave0.ready))


  val forkInput = slave Stream(Bits(8 bits))
  val forkOutputs = Vec(master Stream(Bits(8 bits)),3)
  (forkOutputs , StreamFork(forkInput,3)).zipped.foreach(_ << _)

  val dispatcherInOrderInput = slave Stream(Bits(8 bits))
  val dispatcherInOrderOutput = Vec(master Stream(Bits(8 bits)),3)
  (dispatcherInOrderOutput , StreamDispatcherInOrder(dispatcherInOrderInput,3)).zipped.foreach(_ << _)

  val streamFlowArbiterStreamInput = slave Stream(Bits(8 bits))
  val streamFlowArbiterFlowInput = slave Flow(Bits(8 bits))
  val streamFlowArbiterOutput = master Flow(Bits(8 bits))
  streamFlowArbiterOutput << StreamFlowArbiter(streamFlowArbiterStreamInput,streamFlowArbiterFlowInput)

  val muxSelect = in UInt(2 bits)
  val muxInputs = Vec(slave Stream(Bits(8 bits)),3)
  val muxOutput = master Stream(Bits(8 bits))
  muxOutput << StreamMux(muxSelect,muxInputs)

  val joinInputs = Vec(slave Stream(Bits(8 bits)),3)
  val joinOutput = master.Event
  joinOutput << StreamJoin(joinInputs)
}



class StreamTesterGhdlBoot extends SpinalTesterGhdlBase {
  override def getName: String = "StreamTester"
  override def createToplevel: Component = new StreamTester
}

class StreamTesterCocotbBoot extends SpinalTesterCocotbBase {
  override def getName: String = "StreamTester"
  override def pythonTestLocation: String = "tester/src/test/python/spinal/StreamTester"
  override def createToplevel: Component = new StreamTester
}