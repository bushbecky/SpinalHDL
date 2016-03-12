package spinal.lib.cpu.riscv.impl

import spinal.core._
import spinal.lib._
import spinal.lib.cpu.riscv.impl.Utils._

trait BranchPrediction
object disable extends BranchPrediction
object static extends BranchPrediction
object dynamic extends BranchPrediction

case class CoreParm(val pcWidth : Int = 32,
                    val addrWidth : Int = 32,
                    val startAddress : Int = 0,
                    val bypassExecute0 : Boolean = true,
                    val bypassExecute1 : Boolean = true,
                    val bypassWriteBack : Boolean = true,
                    val pendingI : Int = 1,
                    val branchPrediction : BranchPrediction = static,
                    val branchPredictorSizeLog2 : Int = 4,
                    val branchPredictorHistoryWidth : Int = 2
                     )

case class CoreInstCmd(implicit p : CoreParm) extends Bundle{
  val pc = UInt(p.addrWidth bit)
}
case class CoreInstRsp(implicit p : CoreParm) extends Bundle{
  val instruction = Bits(32 bit)
}


case class CoreDataCmd(implicit p : CoreParm) extends Bundle{
  val wr = Bool
  val address = UInt(p.addrWidth bit)
  val data = Bits(32 bit)
  val size = UInt(2 bit)
}

case class BranchPredictorLine(implicit p : CoreParm)  extends Bundle{
  val pc = UInt(p.pcWidth-p.branchPredictorSizeLog2-2 bit)
  val history = SInt(2 bit)
}
// assert(latencyAnalysis(io.iCmd.data,io.iRsp.data) == 1)
class Core(implicit p : CoreParm) extends Component{
  import p._
  assert(pendingI == 1)
  val io = new Bundle{
    val i = new Bundle {
      val flush = out Bool
      val cmd = master Stream (CoreInstCmd())
      val rsp = slave Stream (CoreInstRsp())
    }
    val d = new Bundle {
      val cmd = master Stream (CoreDataCmd())
      val rsp = slave Flow (Bits(32 bit))
    }
  }
  io.i.flush := False
  val regFile = Mem(Bits(32 bit),32)
  val brancheCache = Mem(BranchPredictorLine(), 1<<branchPredictorSizeLog2)

  //Send instruction request to io.i.cmd
  val fetchCmd = new Area {
    val pc = Reg(UInt(pcWidth bit)) init(U(startAddress,pcWidth bit))
    val inc = RegInit(False) //when io.i.cmd is stalled, it's used as a token to continue the request the next cycle
    val pcNext = pc + Mux(inc,U(4),U(0))
    val pcLoad = Flow(pc)
    when(pcLoad.valid){
      pcNext := pcLoad.payload
    }

    io.i.cmd.valid := True
    io.i.cmd.pc := pcNext
    when(io.i.cmd.fire || pcLoad.fire){
      pc := pcNext
    }

    when(io.i.cmd.fire){
      inc := True
    }.elsewhen(pcLoad.valid){
      inc := False
    }

    val outInst = Stream(wrap(new Bundle{
      val pc = UInt(pcWidth bit)
    }))
    outInst.valid := io.i.cmd.fire
    outInst.pc := pcNext
  }

  //Join fetchCmd.outInst with io.i.rsp
  val fetch = new Area {
    val inContext = fetchCmd.outInst.throwWhen(io.i.flush).m2sPipe()
    inContext.ready := io.i.rsp.ready

    val outInst = Stream(wrap(new Bundle {
      val pc = UInt(pcWidth bit)
      val instruction = Bits(32 bit)
      val ctrl = InstructionCtrl()
      val branchCacheLine = BranchPredictorLine()
    }))
    outInst.arbitrationFrom(io.i.rsp)
    outInst.pc := inContext.pc
    outInst.instruction := io.i.rsp.instruction
    outInst.branchCacheLine := brancheCache.readSync(Mux(inContext.isStall,inContext.pc,fetchCmd.outInst.pc)(2, branchPredictorSizeLog2 bit))
    outInst.ctrl := InstructionCtrl(outInst.instruction)
  }
  val decode = new Area{
    val inInst = fetch.outInst.throwWhen(io.i.flush).m2sPipe()
    val ctrl = inInst.ctrl
    val hazard = Bool //Used to stall decode phase because of register file hazard
    
    val addr0 = inInst.instruction(src0Range).asUInt
    val addr1 = inInst.instruction(src1Range).asUInt

    //read register file
    val srcInstruction = Mux(inInst.isStall,inInst.instruction,fetch.outInst.instruction)
    val src0 = regFile.readSync(srcInstruction(src0Range).asUInt)
    val src1 = regFile.readSync(srcInstruction(src1Range).asUInt)

    val outInst = Stream(wrap(new Bundle{
      val pc = UInt(pcWidth bit)
      val instruction = Bits(32 bit)
      val ctrl = InstructionCtrl()
      val src0 = Bits(32 bit)
      val src1 = Bits(32 bit)
      val alu_op0 = Bits(32 bit)
      val alu_op1 = Bits(32 bit)
      val doSub = Bool
      val predictorHasBranch = Bool
      val branchHistory = Flow(SInt(branchPredictorHistoryWidth bit))
    }))

    val imm = IMM(inInst.instruction)

    // calculate branch target
    val brjmpImm = Mux(ctrl.jmp, imm.j_sext, imm.b_sext)
    val brJumpPc = inInst.pc + brjmpImm.asUInt

    // branch prediction
    val branchCacheHit = inInst.branchCacheLine.pc === inInst.pc(pcWidth-1 downto 2 + branchPredictorSizeLog2)
    val staticBranchPrediction = brjmpImm.msb || ctrl.br === BR.J
    val shouldTakeBranch = Bool
    branchPrediction match{
      case `disable` =>
        shouldTakeBranch := False
      case `static` =>
        shouldTakeBranch := staticBranchPrediction
      case `dynamic` =>
        shouldTakeBranch := staticBranchPrediction
        when(branchCacheHit) {
          shouldTakeBranch := inInst.branchCacheLine.history.msb
        }
    }

    // branch interface
    val pcLoad = Flow(UInt(pcWidth bit))
    pcLoad.valid := inInst.valid && !hazard && outInst.ready && (ctrl.br =/= BR.JR && ctrl.br =/= BR.N) && ctrl.instVal && shouldTakeBranch
    pcLoad.payload := brJumpPc
    

    outInst.arbitrationFrom(inInst.haltWhen(hazard))
    outInst.pc := inInst.pc
    outInst.instruction := inInst.instruction
    outInst.ctrl := ctrl
    outInst.doSub := outInst.ctrl.alu =/= ALU.ADD
    outInst.src0 := Mux(addr0 =/= 0, src0, B(0, 32 bit))
    outInst.src1 := Mux(addr1 =/= 0, src1, B(0, 32 bit))
    outInst.alu_op0 := outInst.ctrl.op1.map(
      default -> outInst.src0,
      OP1.IMU -> imm.u.resized,
      OP1.IMZ -> imm.z.resized,
      OP1.IMJB -> Mux(ctrl.jmp, imm.j_sext, imm.b_sext).resized
    )
    outInst.alu_op1 := outInst.ctrl.op2.map(
      default -> outInst.src1,
      OP2.IMI -> imm.i_sext.resized,
      OP2.IMS -> imm.s_sext.resized,
      OP2.PC1 -> inInst.pc.asBits
    )
    outInst.predictorHasBranch := pcLoad.valid
    outInst.branchHistory.valid := branchCacheHit
    outInst.branchHistory.payload := inInst.branchCacheLine.history

    val flush = False
    when(flush){
      io.i.flush := True
      inInst.ready := True
    }
  }


  val execute0 = new Area {
    val inInst = decode.outInst.m2sPipe()
    val ctrl = inInst.ctrl
    val addr0 = inInst.instruction(19 downto 15).asUInt
    val addr1 = inInst.instruction(24 downto 20).asUInt

    val imm = IMM(inInst.instruction)

    //conditional branch calculation
    val br = new Area {
      val signed = BR.isSignedComp(ctrl.br)
      val src0Ext = (inInst.src0.msb && signed) ## inInst.src0
      val src1Ext = (inInst.src1.msb && signed) ## inInst.src1
      val ltx =  (src0Ext.asUInt-src1Ext.asUInt).msb
      val eq = inInst.src0 === inInst.src1
    }

    val alu = new Alu
    alu.io.func := inInst.ctrl.alu
    alu.io.doSub := inInst.doSub
    alu.io.src0 := inInst.alu_op0
    alu.io.src1 := inInst.alu_op1

    val outInst = Stream(wrap(new Bundle{
      val pc = UInt(pcWidth bit)
      val ctrl = InstructionCtrl()
      val br = new Bundle {
        val eq, ltx = Bool
      }
      val src1 = Bits(32 bit)
      val instruction = Bits(32 bit)
      val alu = Bits(32 bit)
      val adder = UInt(32 bit)
      val shift = Bits(32 bit)
      val predictorHasBranch = Bool
      val branchHistory = Flow(SInt(branchPredictorHistoryWidth bit))
      val pcPlus4 = UInt(32 bit)
    }))
    outInst.arbitrationFrom(inInst)
    outInst.pc := inInst.pc
    outInst.instruction := inInst.instruction
    outInst.predictorHasBranch := inInst.predictorHasBranch
    outInst.branchHistory.valid   := inInst.branchHistory.valid
    outInst.branchHistory.payload := inInst.branchHistory.payload
    outInst.ctrl := ctrl
    outInst.br.eq := br.eq
    outInst.br.ltx := br.ltx
    outInst.src1 := inInst.src1
    outInst.alu := alu.io.result
    outInst.adder := alu.io.adder
    outInst.shift := alu.io.shift
    outInst.pcPlus4 := inInst.pc + 4


    val flush = False
    when(flush){
      io.i.flush := True
      decode.inInst.ready := True
      decode.outInst.valid := False
      inInst.ready := True
      outInst.valid := False
    }
  }

  val execute1 = new Area {
    val inInst = execute0.outInst.m2sPipe()

    // Send memory read/write requests
    io.d.cmd.valid := inInst.fire && inInst.ctrl.men
    io.d.cmd.wr := inInst.ctrl.m === M.XWR
    io.d.cmd.address := inInst.alu.asUInt
    io.d.cmd.payload.data := inInst.src1
    io.d.cmd.size := inInst.ctrl.msk.map(
      default -> U(2), //W
      MSK.B -> U(0),
      MSK.H -> U(1)
    )

    //branch calculation
    val take_evec = False
    val ctrl_pc_sel = inInst.ctrl.br.map[PC.T](
      default -> PC.INC,
      BR.NE -> Mux(!inInst.br.eq, PC.BR1, PC.INC),
      BR.EQ -> Mux(inInst.br.eq, PC.BR1, PC.INC),
      (BR.GE , BR.GEU) -> Mux(!inInst.br.ltx, PC.BR1, PC.INC),
      (BR.LT , BR.LTU)  -> Mux(inInst.br.ltx, PC.BR1, PC.INC),
      BR.J -> PC.J,
      BR.JR -> PC.JR
    )

    when(take_evec) {
      ctrl_pc_sel := PC.EXC
    }

    // branche interface
    val pcLoad = Flow(UInt(pcWidth bit))
    pcLoad.valid := inInst.fire && inInst.ctrl.instVal && ((ctrl_pc_sel =/= PC.INC) =/= inInst.predictorHasBranch)
    pcLoad.payload := ctrl_pc_sel.map(
      default -> inInst.adder,
      PC.EXC -> U(startAddress),
      PC.INC -> inInst.pcPlus4
    )
    
    // dynamic branch predictor history update
    when(inInst.fire && inInst.ctrl.br =/= BR.JR && inInst.ctrl.br =/= BR.N && inInst.ctrl.br =/= BR.J){
      val line = BranchPredictorLine()
      val newHistory = inInst.branchHistory.payload.resize(branchPredictorHistoryWidth + 1) + Mux(ctrl_pc_sel === PC.INC,S(1),S(-1))
      line.pc := inInst.pc(pcWidth-1 downto 2 + branchPredictorSizeLog2)
      when(inInst.branchHistory.valid){
        line.history := newHistory.resized
      }otherwise {
        line.history := (ctrl_pc_sel =/= PC.INC).asSInt.resized
      }
      when(newHistory(newHistory.high downto newHistory.high - 1) =/= S"10") { //no history overflow
        brancheCache(inInst.pc(2, branchPredictorSizeLog2 bit)) := line
      }
    }

    val outInst = Stream(wrap(new Bundle {
      val pc = UInt(pcWidth bit)
      val alu = Bits(32 bit)
      val regFileAddress = UInt(5 bit)
      val ctrl = InstructionCtrl()
      val instruction = Bits(32 bit)
      val pcPlus4 = UInt(32 bit)
    }))
    outInst.arbitrationFrom(inInst.haltWhen((inInst.valid && inInst.ctrl.men && !io.d.cmd.ready)))
    outInst.pc := inInst.pc
    outInst.alu := inInst.ctrl.alu.map(
      (ALU.SLL1) -> Reverse(inInst.shift),
      (ALU.SRL1,ALU.SRA1) -> inInst.shift,
      default -> inInst.alu
    )
    outInst.regFileAddress := inInst.instruction(dstRange).asUInt
    outInst.ctrl := inInst.ctrl
    outInst.instruction := inInst.instruction
    outInst.pcPlus4 := inInst.pcPlus4

  }

  val writeBack = new Area{
    val inInst = execute1.outInst.m2sPipe()

    val dataRspFormated = inInst.ctrl.msk.map(
      default -> io.d.rsp.payload, //W
      MSK.B   -> B(default -> (io.d.rsp.payload(7) && ! inInst.instruction(14)),(7 downto 0) -> io.d.rsp.payload(7 downto 0)),
      MSK.H   -> B(default -> (io.d.rsp.payload(15) && ! inInst.instruction(14)),(15 downto 0) -> io.d.rsp.payload(15 downto 0))
    )

    val regFileData = inInst.ctrl.wb.map (
      default -> B(0,32 bit), //CSR1
      WB.ALU1 -> inInst.alu,
      WB.MEM  -> dataRspFormated,
      WB.PC4  -> (inInst.pcPlus4).asBits.resized
    )

    //Keep the last cycle register file write (for hazard tracking/bypassing)
    val regFileWriteBack = new Area{
      val valid = RegNext(False)
      val addr = RegNext(inInst.regFileAddress)
      val data = RegNext(regFileData)
    }

    when(inInst.fire && inInst.ctrl.rfen) {
      regFileWriteBack.valid := True
      regFile(inInst.regFileAddress) := regFileData
    }
    inInst.ready := inInst.ctrl.wb =/= WB.MEM || inInst.ctrl.m =/= M.XRD || io.d.rsp.valid
  }


  // apply decode/execute1 pcLoad interfaces to fetchCmd.pcLoad
  val branchArbiter = new Area {
    branchPrediction match{
      case `disable` =>
        fetchCmd.pcLoad.valid := execute1.pcLoad.valid
        fetchCmd.pcLoad.payload := execute1.pcLoad.payload
        when(execute1.pcLoad.valid) {
          execute0.flush := True
        }
      case `static` | `dynamic` =>
        fetchCmd.pcLoad.valid := decode.pcLoad.valid
        fetchCmd.pcLoad.payload := decode.pcLoad.payload
        when(decode.pcLoad.valid){
          decode.flush := True
        }
        when(execute1.pcLoad.valid){
          execute0.flush :=  True
          fetchCmd.pcLoad.valid := True
          fetchCmd.pcLoad.payload := execute1.pcLoad.payload
        }
    }

    val loadCounter = Counter(1<<30,execute1.pcLoad.valid).value.keep()
    val flushCounter = Counter(1<<30,io.i.flush).value.keep()
  }

  // Check hazard and apply bypass logic
  val hazardTracker = new  Area {
    val addr0Check = decode.addr0 =/= 0
    val addr1Check = decode.addr1 =/= 0
    val src0Hazard = False
    val src1Hazard = False
    decode.hazard := src0Hazard || src1Hazard

    // write back bypass and hazard
    if(bypassWriteBack) {
      when(writeBack.regFileWriteBack.valid) {
        when(writeBack.regFileWriteBack.addr === decode.addr0) {
          decode.src0 := writeBack.regFileWriteBack.data
        }
        when(writeBack.regFileWriteBack.addr === decode.addr1) {
          decode.src1 := writeBack.regFileWriteBack.data
        }
      }
      when(writeBack.inInst.ctrl.rfen && writeBack.inInst.valid) {
        when(writeBack.inInst.regFileAddress === decode.addr0) {
          decode.src0 := writeBack.regFileData
        }
        when(writeBack.inInst.regFileAddress === decode.addr1) {
          decode.src1 := writeBack.regFileData
        }
      }
    }else{
      when(writeBack.regFileWriteBack.valid) {
        when(addr0Check && decode.addr0 === writeBack.regFileWriteBack.addr) {
          src0Hazard := True
        }
        when(addr1Check && decode.addr1 === writeBack.regFileWriteBack.addr) {
          src1Hazard := True
        }
      }
      when(writeBack.inInst.valid && writeBack.inInst.ctrl.rfen) {
        when(addr0Check && decode.addr0 === writeBack.inInst.regFileAddress) {
          src0Hazard := True
        }
        when(addr1Check && writeBack.inInst.valid && decode.addr1 === writeBack.inInst.regFileAddress && writeBack.inInst.ctrl.rfen) {
          src1Hazard := True
        }
      }
    }

    // execute1 bypass and hazard
    val E1 = new Area{
      val addr0Match = execute1.outInst.instruction(dstRange).asUInt === decode.addr0
      val addr1Match = execute1.outInst.instruction(dstRange).asUInt === decode.addr1
      if(bypassExecute1) {
        when(execute1.outInst.ctrl.execute1AluBypass && execute1.outInst.ctrl.rfen && execute1.inInst.valid) {
          when(addr0Match) {
            decode.src0 := execute1.outInst.alu
            src0Hazard := False
          }
          when(addr1Match) {
            decode.src1 := execute1.outInst.alu
            src1Hazard := False
          }
        }
      }
      when(execute1.inInst.valid && execute1.inInst.ctrl.rfen && (Bool(!bypassExecute1) || !execute1.inInst.ctrl.execute1AluBypass)) {
        when(addr0Check && addr0Match) {
          src0Hazard := True
        }
        when(addr1Check && addr1Match) {
          src1Hazard := True
        }
      }
    }

    // execute0 bypass and hazard
    val E0 = new Area {
      val addr0Match = execute0.outInst.instruction(dstRange).asUInt === decode.addr0
      val addr1Match = execute0.outInst.instruction(dstRange).asUInt === decode.addr1
      if (bypassExecute0) {
        when(execute0.outInst.ctrl.execute0AluBypass && execute0.outInst.ctrl.rfen && execute0.inInst.valid) {
          when(addr0Match) {
            decode.src0 := execute0.outInst.alu
            src0Hazard := False
          }
          when(addr1Match) {
            decode.src1 := execute0.outInst.alu
            src1Hazard := False
          }
        }
      }
      when(execute0.inInst.valid && execute0.inInst.ctrl.rfen && (Bool(!bypassExecute0) || !execute0.inInst.ctrl.execute0AluBypass)) {
        when(addr0Check && addr0Match) {
          src0Hazard := True
        }
        when(addr1Check && addr1Match) {
          src1Hazard := True
        }
      }
    }
  }
}




object CoreMain{
  def main(args: Array[String]) {
    implicit val p = CoreParm(
      pcWidth = 32,
      addrWidth = 32,
      startAddress = 0x200,
      branchPrediction = dynamic,
      bypassExecute0 = true,
      bypassExecute1 = true,
      bypassWriteBack = true,
      branchPredictorSizeLog2 = 7)
    SpinalVhdl(new Core(),_.setLibrary("riscv"))
  }
}

