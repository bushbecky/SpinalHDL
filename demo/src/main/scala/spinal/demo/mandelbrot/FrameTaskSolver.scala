package spinal.demo.mandelbrot

import spinal.core._
import spinal.lib._
import spinal.lib.math.SIntMath


class FrameTaskSolver(p: MandelbrotCoreParameters) extends Component {
  val io = new Bundle {
    val frameTask = slave Stream FrameTask(p)
    val pixelResult = master Stream Fragment(PixelResult(p))
  }

  val pixelTaskGenerator = new PixelTaskGenerator(p)
  val pixelTaskDispatcher = new DispatcherInOrder(Fragment(PixelTask(p)), p.pixelTaskSolverCount)
  val pixelTaskSolver = List.fill(p.pixelTaskSolverCount)(new PixelTaskSolver(p))
  val pixelTaskResultArbiter = StreamArbiter.inOrder.build(Fragment(PixelResult(p)), p.pixelTaskSolverCount)

  pixelTaskGenerator.io.frameTask << io.frameTask
  pixelTaskDispatcher.io.input <-/< pixelTaskGenerator.io.pixelTask
  for (solverId <- 0 until p.pixelTaskSolverCount) {
    pixelTaskSolver(solverId).io.pixelTask <-/< pixelTaskDispatcher.io.outputs(solverId)
    pixelTaskResultArbiter.io.inputs(solverId) </< pixelTaskSolver(solverId).io.result
  }
  io.pixelResult <-< pixelTaskResultArbiter.io.output
}

//  The scala-like way to do the FrameTaskSolver stuff is :

//  val pixelTaskGenerator = new PixelTaskGenerator(p)
//  pixelTaskGenerator.io.frameTask << io.frameTask
//
//  val pixelTaskDispatcher = new DispatcherInOrder(Fragment(PixelTask(p)), p.unitCount)
//  pixelTaskDispatcher.io.input <-/< pixelTaskGenerator.io.pixelTask
//
//  val pixelTaskSolver = List.fill(p.pixelTaskSolverCount)(new PixelTaskSolver(p))
//  (pixelTaskSolver, pixelTaskDispatcher.io.outputs).zipped.foreach(_.io.pixelTask <-/< _)
//
//  val pixelTaskResultArbiter = StreamArbiter.inOrder.build(Fragment(PixelResult(p)), p.unitCount)
//  (pixelTaskSolver, pixelTaskResultArbiter.io.inputs).zipped.foreach(_.io.result >/> _)
//
//  pixelTaskResultArbiter.io.output >-> io.pixelResult


class PixelTaskGenerator(p: MandelbrotCoreParameters) extends Component {
  val io = new Bundle {
    val frameTask = slave Stream FrameTask(p)
    val pixelTask = master Stream Fragment(PixelTask(p))
  }

  val positionOnScreen = Reg(UInt2D(log2Up(p.screenResX) bit, log2Up(p.screenResY) bit))
  val positionOnMandelbrot = Reg(SFix2D(io.frameTask.data.fullRangeSFix))
  val setup = RegInit(True)

  val solverTask = Stream(Fragment(PixelTask(p)))

  io.frameTask.ready := !io.frameTask.valid


  when(io.frameTask.ready) {
    setup := True
  }

  when(io.frameTask.valid && solverTask.ready) {
    when(setup) {
      setup := False
      positionOnScreen.x := 0
      positionOnScreen.y := 0
      positionOnMandelbrot := io.frameTask.data.start
    }.otherwise {
      when(positionOnScreen.x !== p.screenResX - 1) {
        positionOnScreen.x := positionOnScreen.x + 1
        positionOnMandelbrot.x := positionOnMandelbrot.x + io.frameTask.data.inc.x
      }.otherwise {
        positionOnScreen.x := 0
        positionOnMandelbrot.x := io.frameTask.data.start.x
        when(positionOnScreen.y !== p.screenResY - 1) {
          positionOnScreen.y := positionOnScreen.y + 1
          positionOnMandelbrot.y := positionOnMandelbrot.y + io.frameTask.data.inc.y
        }.otherwise {
          positionOnMandelbrot.y := io.frameTask.data.start.y
          io.frameTask.ready := True //Asyncronous acknoledge into syncronous space <3
        }
      }
    }
  }

  solverTask.valid := io.frameTask.valid && !setup;
  solverTask.last := io.frameTask.ready
  solverTask.fragment.mandelbrotPosition := positionOnMandelbrot
  solverTask >-> io.pixelTask

}

class PixelTaskSolver(p: MandelbrotCoreParameters) extends Component {
  val io = new Bundle {
    val pixelTask = slave Stream Fragment(PixelTask(p))
    val result = master Stream Fragment(PixelResult(p))
  }

  //It's the context definition used by each stage of the pipeline, Each task are translated to context ("thread")
  class Context extends Bundle {
    val task = PixelTask(p)
    val lastPixel = Bool
    val done = Bool
    val order = UInt(5 bit)
    //Used to reorder result in same oder than input task
    val iteration = UInt(p.iterationWidth bit)
    val z = SFix2D(p.fix)
  }

  //Extended context with x*x   y*y   x*y result
  class Stage2Context extends Context {
    val zXzX = p.fix
    val zYzY = p.fix
    val zXzY = p.fix
  }

  val insertTaskOrder = Counter(32, io.pixelTask.fire)

  //Task to insert into the pipeline
  val taskToInsert: Stream[Context] = io.pixelTask.translateInto(Stream(new Context))((to, from) => {
    to.task := from.fragment
    to.lastPixel := from.last
    to.done := False
    to.order := insertTaskOrder;
    to.iteration := 0
    to.z := from.fragment.mandelbrotPosition
  })
  val loopBack = RegFlow(new Context)
  //Loop back from end of pipeline
  val stage0 = StreamFlowArbiter(taskToInsert, loopBack) //First stage


  //Stage1 is a routing stage
  val stage1 = Delay(stage0, 2)


  //Stage2 get multiplication result of x*x  y*y and x*y
  val stage2 = RegFlow(new Stage2Context)

  def fixMul(a: SFix, b: SFix): SFix = {
    val ret = SFix(a.exp + b.exp, a.bitCount + b.bitCount bit)
    // SIntMath.mul is a pipelined implementation of the signed multiplication operator
    //(leftSigned, rightSigned, number of bit per multiplicator, trunk bits bellow this limit, ...)
    ret.raw := SIntMath.mul(a.raw, b.raw, 17, p.fixWidth - p.fixExp, 1, (stage, level) => RegNext(stage))
    ret
  }

  stage2.data.zXzX := fixMul(stage1.data.z.x, stage1.data.z.x)
  stage2.data.zYzY := fixMul(stage1.data.z.y, stage1.data.z.y)
  stage2.data.zXzY := fixMul(stage1.data.z.x, stage1.data.z.y)
  stage2 assignSomeByName Delay(stage1, latencyAnalysis(stage1.data.z.x.raw, stage2.data.zXzX.raw) - 1)


  //Stage3 calculate next position of the iteration (zX,zY)
  //       Increment the iteration count if was not done
  //       calculate if the "Thread" has rush a end condition (iteration > N, x*x+y*y + 4)
  val stage3 = RegFlow(new Context)
  stage3 assignAllByName stage2

  stage3.data.z.x := stage2.data.zXzX - stage2.data.zYzY + stage2.data.task.mandelbrotPosition.x
  stage3.data.z.y := (stage2.data.zXzY << 1) + stage2.data.task.mandelbrotPosition.y
  when(!stage2.data.done) {
    stage3.data.iteration := stage2.data.iteration + 1
  }
  when(stage2.data.iteration >= p.iterationLimit | stage2.data.zXzX + stage2.data.zYzY >= 4.0) {
    stage3.data.done := True
  }


  //End Stage put to the output the result if it's finished and in right order
  //          else put it into the feedback to redo iteration or to waiting
  val result = Stream(Fragment(PixelResult(p)))
  val resultOrder = Counter(32, result.fire)
  val readyForResult = stage3.data.done && resultOrder === stage3.data.order


  result.valid := stage3.valid && readyForResult
  result.last := stage3.data.lastPixel
  result.fragment.iteration := stage3.data.iteration - 1


  result >-> io.result

  loopBack.valid := stage3.valid && ((!readyForResult) || (!result.ready))
  loopBack.data := stage3.data
}