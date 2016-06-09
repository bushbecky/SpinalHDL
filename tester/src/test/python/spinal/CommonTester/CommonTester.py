import cocotb
from cocotb.triggers import Timer, Edge, RisingEdge

from spinal.common.misc import setBit, randSignal, assertEguality


class Ref:
    def __init__(self,dut):
        self.io_complexLiteral = 5986
        self.io_inAABits =   (int(dut.io_outAA_bod_gggg) << 0) + \
                             (int(dut.io_outAA_bod_aosi) << 1) + \
                             (int(dut.io_outAA_ahe) << 4) + \
                             (int(dut.io_outAA_zwg) << 5) + \
                             (int(dut.io_outAA_vsw) << 6) + \
                             (int(dut.io_outAA_lwee) << 7)

        self.io_outAABits = (int(dut.io_inAA_bod_gggg) << 0) + \
                            (int(dut.io_inAA_bod_aosi) << 1) + \
                            (int(dut.io_inAA_ahe) << 4) + \
                            (int(dut.io_inAA_zwg) << 5) + \
                            (int(dut.io_inAA_vsw) << 6) + \
                            (int(dut.io_inAA_lwee) << 7)

        self.io_outUIntAdder = (int(dut.io_inUIntA) + int(dut.io_inUIntB)) & 0xFF

        self.io_assign_bitDemux = 0
        self.io_assign_bitDemux = setBit(self.io_assign_bitDemux,int(dut.io_assign_sel_0),int(dut.io_conds_0))
        if int(dut.io_conds_1) == 1:
            self.io_assign_bitDemux = setBit(self.io_assign_bitDemux, int(dut.io_assign_sel_1), int(dut.io_conds_2))
        elif int(dut.io_conds_3) == 1:
            self.io_assign_bitDemux = setBit(self.io_assign_bitDemux, int(dut.io_assign_sel_0), int(dut.io_conds_4))
        if int(dut.io_conds_5) == 1:
            self.io_assign_bitDemux = setBit(self.io_assign_bitDemux, int(dut.io_assign_sel_1), int(dut.io_conds_6))
        self.io_assign_bitDemux = setBit(self.io_assign_bitDemux, 5, 1)



@cocotb.test()
def test1(dut):
    dut.log.info("Cocotb test boot")
    #random.seed(0)


    for i in range(0,1000):
        randSignal(dut.io_conds_0)
        randSignal(dut.io_conds_1)
        randSignal(dut.io_conds_2)
        randSignal(dut.io_conds_3)
        randSignal(dut.io_conds_4)
        randSignal(dut.io_conds_5)
        randSignal(dut.io_conds_6)
        randSignal(dut.io_conds_7)

        randSignal(dut.io_inAA_bod_gggg)
        randSignal(dut.io_inAA_bod_aosi)
        randSignal(dut.io_inAA_ahe)
        randSignal(dut.io_inAA_zwg)
        randSignal(dut.io_inAA_vsw)
        randSignal(dut.io_inAA_lwee)
        randSignal(dut.io_inAABits)

        randSignal(dut.io_inUIntA)
        randSignal(dut.io_inUIntB)

        randSignal(dut.io_assign_sel_0)
        randSignal(dut.io_assign_sel_1)
        randSignal(dut.io_assign_sel_2)
        randSignal(dut.io_assign_sel_3)
        yield Timer(1000)
        ref = Ref(dut)
        assertEguality(ref.io_complexLiteral,dut.io_complexLiteral,"io_complexLiteral")
        assertEguality(ref.io_inAABits, dut.io_inAABits, "io_inAABits")
        assertEguality(ref.io_outAABits, dut.io_outAABits, "io_outAABits")
        assertEguality(ref.io_outUIntAdder, dut.io_outUIntAdder, "io_outUIntAdder")
        assertEguality(ref.io_assign_bitDemux, dut.io_assign_bitDemux, "io_assign_bitDemux")

    dut.log.info("Cocotb test done")
