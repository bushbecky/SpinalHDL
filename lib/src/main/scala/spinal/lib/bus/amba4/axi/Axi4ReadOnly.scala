package spinal.lib.bus.amba4.axi

import spinal.core._
import spinal.lib._

case class Axi4ReadOnly(config: Axi4Config) extends Bundle with IMasterSlave with Axi4Bus{
  val ar = Stream(Axi4Ar(config))
  val r = Stream(Axi4R(config))

  def readCmd = ar
  def readRsp = r


  def <<(that : Axi4) : Unit = that >> this
  def >> (that : Axi4) : Unit = {
    this.readCmd drive that.readCmd
    that.readRsp drive this.readRsp
  }

  def <<(that : Axi4ReadOnly) : Unit = that >> this
  def >> (that : Axi4ReadOnly) : Unit = {
    this.readCmd drive that.readCmd
    that.readRsp drive this.readRsp
  }

  def arValidPipe() : Axi4ReadOnly = {
    val sink = Axi4ReadOnly(config)
    sink.ar << this.ar.validPipe()
    sink.r  >> this.r
    sink
  }

  def setIdle(): this.type = {
    this.readCmd.setIdle()
    this.readRsp.setBlocked()
    this
  }

  def setBlocked(): this.type = {
    this.readCmd.setBlocked()
    this.readRsp.setIdle()
    this
  }

  def toAxi4(): Axi4 = {
    val ret = Axi4(config)
    this >> ret
  
    ret.writeCmd.setIdle()
    ret.writeData.setIdle()
    ret.writeRsp.setBlocked()

    ret
  }

  def toFullConfig(): Axi4ReadOnly = {
    val ret = Axi4ReadOnly(config.toFullConfig())
    ret << this
    ret
  }

  def pipelined(
    ar: StreamPipe = StreamPipe.NONE,
    r: StreamPipe = StreamPipe.NONE
  ): Axi4ReadOnly = {
    val ret = cloneOf(this)
    ret.ar << this.ar.pipelined(ar)
    ret.r.pipelined(r) >> this.r
    ret
  }

  override def asMaster(): Unit = {
    master(ar)
    slave(r)
  }

  def formalContext(maxBursts: Int = 16) = new Area {
    import spinal.core.formal._
    val addrChecker = ar.payload.formalContext()

    val oRecord = FormalAxi4Record(config, 0).init()

    val histInput = Flow(cloneOf(oRecord))
    histInput.payload := oRecord
    histInput.valid := False
    val hist = HistoryModifyable(histInput, maxBursts)
    hist.io.inStreams.map(_.valid := False)
    hist.io.inStreams.map(_.payload := oRecord)
    hist.io.outStreams.map(_.ready := False)

    val (arExist, arId) = hist.findFirst(x => x.valid && !x.axDone)
    val (doneExist, doneId) = hist.findFirst(x => x.valid && x.axDone)
    when(doneExist & arExist) { assert(arId < doneId) }

    val (rExist, rId) =
      hist.findFirst(x => x.valid && !x.seenLast && { if (config.useId) r.id === x.id else True })
    for (i <- hist.io.outStreams.indices) {
      when (rExist & i < rId) { assert(hist.io.outStreams(i).count === 0) }
    }

    val (rmExist, rmId) =
      hist.findFirst(x => x.valid && x.seenLast && x.axDone)
    when(rmExist) { hist.io.outStreams(rmId).ready := True }

    val errors = new Area {
      val reset = ClockDomain.current.isResetActive
      val ValidWhileReset = (reset | past(reset)) & (ar.valid === True)
      val RespWhileReset = (reset | past(reset)) & (r.valid === True)
      val DataNumberDonotFitLen = CombInit(False)
      val NoAddrRequest = CombInit(False)
      val WrongResponseForExAccesss = CombInit(False)
    }

    val arRecord = CombInit(oRecord)
    val arValid = False
    val addressLogic = new Area {
      when(ar.valid) {
        val ax = ar.asInstanceOf[Stream[Axi4Ax]]
        when(arExist) {
          arRecord := hist.io.outStreams(arId)
          arRecord.allowOverride
          arRecord.assignFromAx(ax)
          arValid := True
        }
          .otherwise { histInput.assignFromAx(ax) }
      }
      when(arValid) {
        hist.io.inStreams(arId).payload := arRecord
        hist.io.inStreams(arId).valid := arValid
      }
    }

    val rRecord = CombInit(oRecord)
    val dataLogic = new Area {
      val selected = CombInit(oRecord)
      when(r.valid) {
        when(rExist) {
          rRecord := hist.io.outStreams(rId)
          selected := hist.io.outStreams(rId)

          rRecord.allowOverride
          rRecord.assignFromR(r, selected)
          hist.io.inStreams(rId).payload := rRecord
          hist.io.inStreams(rId).valid := True

          errors.NoAddrRequest := !selected.axDone
          if (config.useResp && config.useLock)
            errors.WrongResponseForExAccesss := selected.axDone & r.resp === Axi4.resp.EXOKAY & !selected.isLockExclusive
          errors.DataNumberDonotFitLen := rRecord.checkLen()

        }.otherwise { errors.NoAddrRequest := True }
      }
    }
    
    when(ar.valid & !arExist) {
      histInput.valid := True
    }

    def withMasterAsserts(maxStallCycles: Int = 0) = {
      ar.withAsserts()
      r.withTimeoutAsserts(maxStallCycles)

      when(ar.valid) {
        addrChecker.withAsserts()
      }
      assert(!errors.ValidWhileReset)
    }

    def withMasterAssumes(maxStallCycles: Int = 0) = {
      ar.withTimeoutAssumes(maxStallCycles)
      r.withAssumes()

      assume(!errors.DataNumberDonotFitLen)
      assume(!errors.NoAddrRequest)
      assume(!errors.WrongResponseForExAccesss)      
      assume(!errors.RespWhileReset)
    }

    def withSlaveAsserts(maxStallCycles: Int = 0) = {
      ar.withTimeoutAsserts(maxStallCycles)
      r.withAsserts()

      assert(!errors.DataNumberDonotFitLen)
      assert(!errors.NoAddrRequest)
      assert(!errors.WrongResponseForExAccesss)
      assert(!errors.RespWhileReset)
    }

    def withSlaveAssumes(maxStallCycles: Int = 0) = {
      ar.withAssumes()
      r.withTimeoutAssumes(maxStallCycles)

      when(ar.valid) {
        addrChecker.withAssumes()
      }
      assume(!errors.ValidWhileReset)
    }

    def withCovers() = {
      ar.withCovers(2)
      when(ar.fire) {
        addrChecker.withCovers()
      }
      r.withCovers(2)
      when(r.fire) {
        r.payload.withCovers()
      }
    }
  }
}
