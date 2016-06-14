package spinal.lib.fsm

import spinal.core._

import scala.collection.mutable.ArrayBuffer

/**
 * Created by PIC32F_USER on 14/06/2016.
 */
trait EntryPoint
class State(implicit stateMachineAccessor : StateMachineAccessor) extends Area{
  val onEntryTasks = ArrayBuffer[() => Unit]()
  val onExitTasks = ArrayBuffer[() => Unit]()
  val whenActiveTasks = ArrayBuffer[() => Unit]()
  val whenIsNextTasks = ArrayBuffer[() => Unit]()
  @dontName var innerFsm = ArrayBuffer[StateMachine]()

  def onEntry(doThat : => Unit) : Unit = onEntryTasks += (() => doThat)
  def onExit(doThat : => Unit) : Unit = onExitTasks += (() => doThat)
  def whenIsActive(doThat : => Unit) : Unit = whenActiveTasks += (() => doThat)
  def whenIsNext(doThat : => Unit) : Unit = whenIsNextTasks += (() => doThat)
  def goto(state : State) = stateMachineAccessor.goto(state)
  def innerFsm(that : => StateMachine) : Unit = innerFsm += that
  def exit() : Unit = stateMachineAccessor.exit()

  val stateId = stateMachineAccessor.add(this)

  if(isInstanceOf[EntryPoint]) stateMachineAccessor.setEntry(this)
}

object StateFsm{
  def apply(exitState : =>  State)(fsm :  StateMachine)(implicit stateMachineAccessor : StateMachineAccessor) : StateFsm = new StateFsm(exitState,fsm)
}

class StateFsm(returnIn : =>  State,val fsm :  StateMachine)(implicit stateMachineAccessor : StateMachineAccessor) extends State{
  onEntry{
    fsm.start()
  }
  whenIsActive{
    when(fsm.wantExit){
      goto(returnIn)
    }
  }
  stateMachineAccessor.add(fsm)
  fsm.autoStart = false
}

object StateMultiFsm{
  def apply(exitState : =>  State)(fsms :  StateMachine*)(implicit stateMachineAccessor : StateMachineAccessor) : StateMultiFsm = new StateMultiFsm(exitState,fsms)
}

class StateMultiFsm(returnIn : =>  State,val fsms :  Seq[StateMachine])(implicit stateMachineAccessor : StateMachineAccessor) extends State{
  onEntry{
    fsms.foreach(_.start())
  }
  whenIsActive{
    when(fsms.map(_.wantExit).reduce(_ && _)){
      goto(returnIn)
    }
  }
  for(fsm <- fsms){
    stateMachineAccessor.add(fsm)
    fsm.autoStart = false
  }
}

class StateExit(implicit stateMachineAccessor : StateMachineAccessor) extends State{
  whenIsActive{
    stateMachineAccessor.exit()
  }
}