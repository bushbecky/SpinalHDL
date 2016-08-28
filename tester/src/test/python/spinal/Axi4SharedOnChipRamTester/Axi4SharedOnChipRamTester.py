import random

import cocotb

from spinal.common.Axi4 import Axi4Shared, Axi4SharedMemoryChecker
from spinal.common.Phase import PhaseManager
from spinal.common.misc import ClockDomainAsyncReset, simulationSpeedPrinter


@cocotb.test()
def test1(dut):
    dut.log.info("Cocotb test boot")
    random.seed(0)

    cocotb.fork(ClockDomainAsyncReset(dut.clk, dut.reset))
    cocotb.fork(simulationSpeedPrinter(dut.clk))

    phaseManager = PhaseManager()
    phaseManager.setWaitTasksEndTime(1000*200)

    checker = Axi4SharedMemoryChecker("checker",phaseManager,Axi4Shared(dut, "io_axi"),12,dut.clk,dut.reset)
    checker.idWidth = 2
    checker.nonZeroReadRspCounterTarget = 2000

    yield phaseManager.run()

    dut.log.info("Cocotb test done")
