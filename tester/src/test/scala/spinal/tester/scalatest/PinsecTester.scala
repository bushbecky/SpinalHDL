package spinal.tester.scalatest

import org.scalatest.Suites
import spinal.core.{SpinalConfig, Component}
import spinal.lib.soc.pinsec.Pinsec

/**
 * Created by PIC32F_USER on 22/08/2016.
*/
class PinsecTesterCocotbBoot extends SpinalTesterCocotbBase {
  override def getName: String = "PinsecTester"
  override def pythonTests: Seq[(String,String)] = List(
    "jtag" -> "tester/src/test/python/spinal/Pinsec/jtag",
    "uart" -> "tester/src/test/python/spinal/Pinsec/uart"
  )
  override def createToplevel: Component = new Pinsec
  override def backendConfig(config: SpinalConfig): SpinalConfig = config.dumpWave()
}
