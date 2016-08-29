import random

import cocotb
from cocotb.triggers import Timer

from spinal.common.Axi4 import Axi4SharedMemoryChecker, Axi4Shared
from spinal.common.Phase import PhaseManager
from spinal.common.Stream import Stream
from spinal.common.misc import simulationSpeedPrinter


@cocotb.coroutine
def ClockDomainAsyncResetCustom(clk,reset):
    if reset:
        reset <= 1
    clk <= 0
    yield Timer(100000)
    if reset:
        reset <= 0
    while True:
        clk <= 0
        yield Timer(3750)
        clk <= 1
        yield Timer(3750)

@cocotb.test()
def test1(dut):
    random.seed(0)

    cocotb.fork(ClockDomainAsyncResetCustom(dut.clk, dut.reset))
    cocotb.fork(simulationSpeedPrinter(dut.clk))

    phaseManager = PhaseManager()
    phaseManager.setWaitTasksEndTime(1000*2000)

    checker = Axi4SharedMemoryChecker("checker",phaseManager,Axi4Shared(dut, "io_axi"),12,dut.clk,dut.reset)
    checker.idWidth = 2
    checker.nonZeroReadRspCounterTarget = 2000

    yield phaseManager.run()

