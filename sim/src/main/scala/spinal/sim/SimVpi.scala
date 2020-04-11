package spinal.sim

import spinal.sim.vpi._
import collection.JavaConverters._

class SimVpi(backend: VpiBackend) extends SimRaw {

  val zeroByte = 0.toByte
  val nativeIface = backend.instanciate()

  override def getInt(signal : Signal) = {
    val id = getSignalId(signal)
    nativeIface.read32(id)
  }

  //for some reason setInt is not in SimRaw...
  def setInt(signal : Signal, value: Int) {
    val id = getSignalId(signal)
    nativeIface.write32(id, value)
  }

  override def getLong(signal : Signal) = {
    val id = getSignalId(signal)
    nativeIface.read64(id)
  }

  override def setLong(signal : Signal, value: Long) {
    val id = getSignalId(signal)
    nativeIface.write64(id, value)
  }

  override def getBigInt(signal : Signal) : BigInt = {
    val id = getSignalId(signal)
    val readVec = nativeIface.read(id)
    if(!signal.dataType.isInstanceOf[SIntDataType]) readVec.add(0, zeroByte) 
    BigInt(readVec.asScala.toArray.map{x => x.toByte})
  }

  override def setBigInt(signal : Signal, value : BigInt) {
    val id = getSignalId(signal)
    signal.dataType.checkBigIntRange(value, signal)
    nativeIface.write(id, new VectorInt8(value.toByteArray))
  }

  override def sleep(cycles : Long) {
    nativeIface.sleep(cycles)
  }
  
  override def eval() : Boolean = {
    nativeIface.eval
    false
  } 
  
  override def end() {
    nativeIface.close
  }

  def getSignalId(signal: Signal) : Long = {
   if (signal.validId) signal.id
   else {
     signal.id = nativeIface.get_signal_handle(signal.toVPIAddress)
     signal.validId = true
     signal.id
   }
  }

  override def enableWave() {}
  override def disableWave() {}
  override def isBufferedWrite = false
}
