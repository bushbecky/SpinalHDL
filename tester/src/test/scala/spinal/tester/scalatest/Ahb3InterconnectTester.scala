package spinal.tester.scalatest

import spinal.core._
import spinal.lib._
import spinal.lib.bus.amba3.ahb.{Ahb3InterconnectFactory, Ahb3Slave, Ahb3Master, Ahb3Config}


object Ahb3InterconnectTester{
  class Ahb3InterconnectTester extends Component {
    val ahbConfig = Ahb3Config(addressWidth = 16,dataWidth = 32)

    val ahbMasters = Vec(slave(Ahb3Master(ahbConfig)),3)
    val ahbSlaves  = Vec(master(Ahb3Slave(ahbConfig)),4)

    val interconnect = Ahb3InterconnectFactory(ahbConfig)
      .addSlaves(
        ahbSlaves(0) -> (0x0000,0x4000),
        ahbSlaves(1) -> (0x4000,0x4000),
        ahbSlaves(2) -> (0x8000,0x4000),
        ahbSlaves(3) -> (0xC000,0x4000)
      )
      .addConnections(
        ahbMasters(0) -> List(ahbSlaves(1),ahbSlaves(2),ahbSlaves(3)),
        ahbMasters(1) -> List(ahbSlaves(0),ahbSlaves(2),ahbSlaves(3)),
        ahbMasters(2) -> List(ahbSlaves(0),ahbSlaves(1),ahbSlaves(3))
      )
      .build()
  }
}
//
//class Ahb3InterconnectTesterCocotbBoot extends SpinalTesterCocotbBase {
//  override def getName: String = "Ahb3InterconnectTester"
//  override def pythonTestLocation: String = "tester/src/test/python/spinal/Ahb3InterconnectTester"
//  override def createToplevel: Component = new Ahb3InterconnectTester.Ahb3InterconnectTester
//}