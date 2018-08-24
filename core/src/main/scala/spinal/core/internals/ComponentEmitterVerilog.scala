/*                                                                           *\
**        _____ ____  _____   _____    __                                    **
**       / ___// __ \/  _/ | / /   |  / /   HDL Core                         **
**       \__ \/ /_/ // //  |/ / /| | / /    (c) Dolu, All rights reserved    **
**      ___/ / ____// // /|  / ___ |/ /___                                   **
**     /____/_/   /___/_/ |_/_/  |_/_____/                                   **
**                                                                           **
**      This library is free software; you can redistribute it and/or        **
**    modify it under the terms of the GNU Lesser General Public             **
**    License as published by the Free Software Foundation; either           **
**    version 3.0 of the License, or (at your option) any later version.     **
**                                                                           **
**      This library is distributed in the hope that it will be useful,      **
**    but WITHOUT ANY WARRANTY; without even the implied warranty of         **
**    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU      **
**    Lesser General Public License for more details.                        **
**                                                                           **
**      You should have received a copy of the GNU Lesser General Public     **
**    License along with this library.                                       **
\*                                                                           */
package spinal.core.internals

import java.io.File

import spinal.core._

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer
import scala.util.Random


class ComponentEmitterVerilog(
  val c                              : Component,
  systemVerilog                      : Boolean,
  verilogBase                        : VerilogBase,
  override val algoIdIncrementalBase : Int,
  override val mergeAsyncProcess     : Boolean,
  asyncResetCombSensitivity          : Boolean,
  anonymSignalPrefix                 : String,
  nativeRom                          : Boolean,
  nativeRomFilePrefix                : String,
  emitedComponentRef                 : java.util.concurrent.ConcurrentHashMap[Component, Component],
  emitedRtlSourcesPath               : mutable.LinkedHashSet[String]
) extends ComponentEmitter {

  import verilogBase._

  override def component = c

  val portMaps     = ArrayBuffer[String]()
  val declarations = new StringBuilder()
  val logics       = new StringBuilder()

  def getTrace() = new ComponentEmitterTrace(declarations :: logics :: Nil, portMaps)

  def result: String = {
    val ret = new StringBuilder()
    var first = true

    ret ++= s"module ${component.definitionName} ("

    for(portMap <- portMaps){
      if(first){
        ret ++= s"\n    $portMap"
        first = false
      } else {
        ret ++= s",\n    $portMap"
      }
    }

    ret ++= s");\n"
    ret ++= declarations
    ret ++= logics
    ret ++= s"endmodule\n"
    ret ++= s"\n"
    ret.toString()
  }

  def emitEntity(): Unit = {
    component.getOrdredNodeIo.foreach(baseType =>
      if(outputsToBufferize.contains(baseType) || baseType.isInput)
        portMaps += s"  ${emitSyntaxAttributes(baseType.instanceAttributes)}${emitDirection(baseType)} ${emitType(baseType)} ${baseType.getName()}${emitCommentAttributes(baseType.instanceAttributes)}"
      else
        portMaps += s"  ${emitSyntaxAttributes(baseType.instanceAttributes)}${emitDirection(baseType)} ${if(signalNeedProcess(baseType) && !outputsToBufferize.contains(baseType)) "reg " else ""}${emitType(baseType)} ${baseType.getName()}${if(outputsToBufferize.contains(baseType)) "" else getBaseTypeSignalInitialisation(baseType)}${emitCommentAttributes(baseType.instanceAttributes)}"
    )
  }

  override def wrapSubInput(io: BaseType): Unit = {
    val name = component.localNamingScope.allocateName(anonymSignalPrefix)
    declarations ++= emitBaseTypeWrap(io, name)
    referencesOverrides(io) = name
  }

  def emitArchitecture(): Unit = {
    for(mem <- mems){
      mem.foreachStatements(s => {
        s.foreachDrivingExpression{
          case e: BaseType =>
          case e           => expressionToWrap += e
        }

        s match {
          case s: MemReadSync  =>
            val name = component.localNamingScope.allocateName(anonymSignalPrefix)
            declarations ++= emitExpressionWrap(s, name, "reg")
            wrappedExpressionToName(s) = name
          case s: MemReadAsync =>
            val name = component.localNamingScope.allocateName(anonymSignalPrefix)
            declarations ++= emitExpressionWrap(s, name)
            wrappedExpressionToName(s) = name
          case s: MemReadWrite =>
            val name = component.localNamingScope.allocateName(anonymSignalPrefix)
            declarations ++= emitExpressionWrap(s, name, "reg")
            wrappedExpressionToName(s) = name
          case s: MemWrite    =>
        }
      })
    }

    for(output <- outputsToBufferize){
      val name = component.localNamingScope.allocateName(anonymSignalPrefix)
      declarations ++= emitBaseTypeSignal(output, name)
      logics ++= s"  assign ${emitReference(output, false)} = $name;\n"
      referencesOverrides(output) = name
    }

    for((select, muxes) <- multiplexersPerSelect){
      expressionToWrap += select._1
      for(mux <- muxes) {
        val name = component.localNamingScope.allocateName(anonymSignalPrefix)
        declarations ++= s"  reg ${emitType(mux)} $name;\n"
        wrappedExpressionToName(mux) = name
        //        expressionToWrap ++= mux.inputs
      }
    }

    component.children.foreach(sub => sub.getAllIo.foreach(io => if(io.isOutput) {
      val name = component.localNamingScope.allocateName(anonymSignalPrefix)
      declarations ++= emitExpressionWrap(io, name)
      referencesOverrides(io) = name
    }))

    //Wrap expression which need it
    cutLongExpressions()
    expressionToWrap --= wrappedExpressionToName.keysIterator
    for(e <- expressionToWrap if !e.isInstanceOf[DeclarationStatement]){
      val name = component.localNamingScope.allocateName(anonymSignalPrefix)
      declarations ++= emitExpressionWrap(e, name)
      wrappedExpressionToName(e) = name
    }

    for(e <- expressionToWrap  if !e.isInstanceOf[DeclarationStatement]){
      logics ++= s"  assign ${wrappedExpressionToName(e)} = ${emitExpressionNoWrappeForFirstOne(e)};\n"
    }

    //Wrap inout
    analogs.foreach(io => {
      io.foreachStatements{
        case AssignmentStatement(_, source: BaseType) =>
          referencesOverrides(source) = emitExpression(io)
        case _ =>
      }
    })

    //Flush all that mess out ^^
    emitSignals()
    emitMems(mems)
    emitSubComponents(openSubIo)
    emitAnalogs()
    emitMuxes()

    processes.foreach(p => {
      if(p.leafStatements.nonEmpty ) {
        p.leafStatements.head match {
          case AssignmentStatement(target: DeclarationStatement, _) if subComponentInputToNotBufferize.contains(target) =>
          case _ => emitAsyncronous(p)
        }
      } else {
        emitAsyncronous(p)
      }
    })

    syncGroups.valuesIterator.foreach(emitSyncronous(component, _))

    component.dslBody.walkStatements{
      case s: TreeStatement => s.algoIncrementale = algoIdIncrementalBase
      case s                =>
    }
  }

  def emitAnalogs(): Unit ={
    analogs.foreach(analog => {
      analog.foreachStatements {
        case AssignmentStatement(target, source: AnalogDriver) => {
          source.getTypeObject match {
            case `TypeBool` => logics ++= s"  assign ${emitAssignedExpression(target)} = ${emitExpression(source.enable)} ? ${emitExpression(source.data)} : 1'bz;\n"
            case `TypeBits` | `TypeUInt` | `TypeSInt` =>
              val width = source.asInstanceOf[WidthProvider].getWidth
              logics ++= s"  assign ${emitAssignedExpression(target)} = ${emitExpression(source.enable)} ? ${emitExpression(source.data)} : $width'b${"z" * width};\n"
            case `TypeEnum` => SpinalError("???")
          }
        }
        case s =>
      }
    })
  }

  def emitSubComponents(openSubIo: mutable.HashSet[BaseType]): Unit = {

    for (child <- component.children) {
      val isBB             = child.isInstanceOf[BlackBox]
      val isBBUsingULogic  = isBB && child.asInstanceOf[BlackBox].isUsingULogic
      val definitionString =  if (isBB) child.definitionName else getOrDefault(emitedComponentRef, child, child).definitionName

      logics ++= s"  $definitionString "

      if (child.isInstanceOf[BlackBox]) {
        val bb = child.asInstanceOf[BlackBox]
        val genericFlat = bb.genericElements

        if (genericFlat.nonEmpty) {
          logics ++= s"#( \n"
          for (e <- genericFlat) {
            e match {
              case (name: String, bt: BaseType) => logics ++= s"    .${name}(${emitExpression(bt.head.source)}),\n"
              case (name: String, s: String)    => logics ++= s"    .${name}(${"\""}${s}${"\""}),\n"
              case (name: String, i: Int)       => logics ++= s"    .${name}($i),\n"
              case (name: String, d: Double)    => logics ++= s"    .${name}($d),\n"
              case (name: String, b: Boolean)   => logics ++= s"    .${name}($b),\n"
            }
          }
          logics.setCharAt(logics.size - 2, ' ')
          logics ++= s"  ) "
        }
      }

      logics ++= s"${child.getName()} ( \n"
      for (data <- child.getOrdredNodeIo) {
        val logic = if(openSubIo.contains(data)) "" else emitReference(data, false)
        logics ++= s"    .${emitReferenceNoOverrides(data)}($logic),\n"
      }
      logics.setCharAt(logics.size - 2, ' ')

      logics ++= s"  );"
      logics ++= s"\n"
    }
  }

  def emitClockedProcess(emitRegsLogic        : (String, StringBuilder) => Unit,
                         emitRegsInitialValue : (String, StringBuilder) => Unit,
                         b                    : mutable.StringBuilder,
                         clockDomain          : ClockDomain,
                         withReset            : Boolean): Unit ={

    val clock       = component.pulledDataCache.getOrElse(clockDomain.clock, throw new Exception("???")).asInstanceOf[Bool]
    val reset       = if (null == clockDomain.reset || !withReset) null else component.pulledDataCache.getOrElse(clockDomain.reset, throw new Exception("???")).asInstanceOf[Bool]
    val softReset   = if (null == clockDomain.softReset || !withReset) null else component.pulledDataCache.getOrElse(clockDomain.softReset, throw new Exception("???")).asInstanceOf[Bool]
    val clockEnable = if (null == clockDomain.clockEnable) null else component.pulledDataCache.getOrElse(clockDomain.clockEnable, throw new Exception("???")).asInstanceOf[Bool]

    val asyncReset = (null != reset) && clockDomain.config.resetKind == ASYNC
    val syncReset  = (null != reset) && clockDomain.config.resetKind == SYNC
    var tabLevel   = 1

    def tabStr = "  " * tabLevel

    def inc = tabLevel = tabLevel + 1

    def dec = tabLevel = tabLevel - 1

    val initialStatlementsGeneration = new StringBuilder()

    referenceSetStart()

    referenceSetAdd(emitClockEdge(emitReference(clock,false),clockDomain.config.clockEdge))


    if(withReset) {
      val initSensitivity = asyncResetCombSensitivity && asyncReset
      if(!initSensitivity) referenceSetPause()
      emitRegsInitialValue("      ", initialStatlementsGeneration)
      if(!initSensitivity) referenceSetResume()
    }

    if (asyncReset) {
      referenceSetAdd(emitResetEdge(emitReference(reset, false), clockDomain.config.resetActiveLevel))
    }

    b ++= s"${tabStr}always @ (${referenceSetSorted().mkString(" or ")}) begin\n"

    inc

    if (asyncReset) {
      b ++= s"${tabStr}if (${if (clockDomain.config.resetActiveLevel == HIGH) "" else "!"}${emitReference(reset, false)}) begin\n"
      inc
      b ++= initialStatlementsGeneration
      dec
      b ++= s"${tabStr}end else begin\n"
      inc
    }

    if (clockEnable != null) {
      b ++= s"${tabStr}if(${if (clockDomain.config.clockEnableActiveLevel == HIGH) "" else "!"}${emitReference(clockEnable, false)}) begin\n"
      inc
    }

    if (syncReset || softReset != null) {
      var condList = ArrayBuffer[String]()
      if(syncReset) condList += s"${if (clockDomain.config.resetActiveLevel == HIGH) "" else "!"}${emitReference(reset, false)}"
      if(softReset != null) condList += s"${if (clockDomain.config.softResetActiveLevel == HIGH) "" else "!"}${emitReference(softReset, false)}"

      b ++= s"${tabStr}if(${condList.reduce(_ + " || " + _)}) begin\n"
      inc
      b ++= initialStatlementsGeneration
      dec
      b ++= s"${tabStr}end else begin\n"
      inc
      emitRegsLogic(tabStr,b)
      dec
      b ++= s"${tabStr}end\n"
      dec
    } else {
      emitRegsLogic(tabStr,b)
      dec
    }

    while (tabLevel != 1) {
      b ++= s"${tabStr}end\n"
      dec
    }
    b ++= s"${tabStr}end\n"
    dec
    b ++= s"${tabStr}\n"
  }

  def emitSyncronous(component: Component, group: SyncGroup): Unit = {
    import group._

    def withReset = hasInit

    def emitRegsInitialValue(tab: String, b: StringBuilder): Unit = {
      emitLeafStatements(group.initStatements, 0, group.scope, "<=", b , tab)
    }

    def emitRegsLogic(tab: String, b: StringBuilder): Unit = {
      emitLeafStatements(group.dataStatements, 0, group.scope, "<=", b , tab)
    }

    emitClockedProcess(
      emitRegsLogic        = emitRegsLogic,
      emitRegsInitialValue = emitRegsInitialValue,
      b                    = logics,
      clockDomain          = group.clockDomain,
      withReset            = withReset
    )
  }

  def emitMuxes(): Unit ={
    for(((select, length), muxes) <- multiplexersPerSelect){
      logics ++= s"  always @(*) begin\n"
      logics ++= s"    case(${emitExpression(select)})\n"
      for(i <- 0 until length){
        val key = Integer.toBinaryString(i)
        if(i != length-1)
          logics ++= s"""      ${select.getWidth}'b${"0" * (select.getWidth - key.length)}${key} : begin\n"""
        else
          logics ++= s"      default : begin\n"

        for(mux <- muxes){
          logics ++= s"        ${wrappedExpressionToName(mux)} = ${emitExpression(mux.inputs(i))};\n"
        }
        logics ++= s"      end\n"
      }
      logics ++= s"    endcase\n"
      logics ++= s"  end\n\n"
    }
  }

  def emitAsyncronousAsAsign(process: AsyncProcess) = process.leafStatements.size == 1 && process.leafStatements.head.parentScope == process.nameableTargets.head.rootScopeStatement

  def emitAsyncronous(process: AsyncProcess): Unit = {
    process match {
      case _ if emitAsyncronousAsAsign(process) =>
        process.leafStatements.head match {
          case s: AssignmentStatement =>
            logics ++= s"  assign ${emitAssignedExpression(s.target)} = ${emitExpression(s.source)};\n"
        }
      case _ =>
        val tmp = new StringBuilder
        referenceSetStart()
        emitLeafStatements(process.leafStatements, 0, process.scope, "=", tmp, "    ")

        if (referenceSetSorted().nonEmpty) {
//          logics ++= s"  always @ (${referenceSetSorted().mkString(" or ")})\n"
          logics ++= s"  always @ (*) begin\n"
          logics ++= tmp.toString()
          logics ++= "  end\n\n"
        } else {
          //assert(process.nameableTargets.size == 1)
          for(node <- process.nameableTargets) node match {
            case node: BaseType =>
              val funcName = "zz_" + emitReference(node, false)
              declarations ++= s"  function ${emitType(node)} $funcName(input dummy);\n"
//              declarations ++= s"    reg ${emitType(node)} ${emitReference(node, false)};\n"
              declarations ++= s"    begin\n"

              val statements = ArrayBuffer[LeafStatement]()
              node.foreachStatements(s => statements += s.asInstanceOf[LeafStatement])

              val oldRef = referencesOverrides.getOrElse(node, null)
              referencesOverrides(node) = funcName
              emitLeafStatements(statements, 0, process.scope, "=", declarations, "      ")

              if(oldRef != null) referencesOverrides(node) = oldRef else referencesOverrides.remove(node)
//              declarations ++= s"      $funcName = ${emitReference(node, false)};\n"
              declarations ++= s"    end\n"
              declarations ++= s"  endfunction\n"

              val name = component.localNamingScope.allocateName(anonymSignalPrefix)
              declarations ++= s"  wire ${emitType(node)} $name;\n"
              logics ++= s"  assign $name = ${funcName}(1'b0);\n"
//              logics ++= s"  always @ ($name) ${emitReference(node, false)} = $name;\n"
              logics ++= s"  always @ (*) ${emitReference(node, false)} = $name;\n"
          }
        }
    }
  }

  def emitLeafStatements(statements: ArrayBuffer[LeafStatement], statementIndexInit: Int, scope: ScopeStatement, assignmentKind: String, b: StringBuilder, tab: String): Int ={
    var statementIndex = statementIndexInit
    var lastWhen: WhenStatement = null

    def closeSubs(): Unit = {
      if(lastWhen != null) {
        b ++= s"${tab}end\n"
        lastWhen = null
      }
    }

    while(statementIndex < statements.length){

      val leaf        = statements(statementIndex)
      val statement   = leaf
      val targetScope = statement.parentScope

      if(targetScope == scope){
        closeSubs()

        statement match {
          case assignment: AssignmentStatement  => b ++= s"${tab}${emitAssignedExpression(assignment.target)} ${assignmentKind} ${emitExpression(assignment.source)};\n"
          case assertStatement: AssertStatement =>
            val cond = emitExpression(assertStatement.cond)

            val frontString = (for(m <- assertStatement.message) yield m match{
              case m: String     => m
              case m: Expression => "%x"
            }).mkString

            val backString = (for(m <- assertStatement.message if m.isInstanceOf[Expression]) yield m match{
              case m: Expression => ", " + emitExpression(m)
            }).mkString

            val keyword = assertStatement.kind match {
              case AssertStatementKind.ASSERT => "assert"
              case AssertStatementKind.ASSUME => "assume"
              case AssertStatementKind.COVER => "cover"
            }


            val assertCond = assertStatement.getTag(classOf[IfDefTag]).getOrElse(null)
            if(assertCond == null)
              b ++= s"`ifndef SYNTHESIS\n"
            else
              b ++= s"`ifdef ${assertCond.cond}\n"

            if(!systemVerilog){
              val severity = assertStatement.severity match{
                case `NOTE`     => "NOTE"
                case `WARNING`  => "WARNING"
                case `ERROR`    => "ERROR"
                case `FAILURE`  => "FAILURE"
              }
              b ++= s"${tab}if(!$cond) begin\n"
              b ++= s"""${tab}  $$display("$severity $frontString"$backString);\n"""
              if (assertStatement.severity == `FAILURE`) b ++= tab + "  $finish;\n"
              b ++= s"${tab}end\n"
            } else {
              val severity = assertStatement.severity match{
                case `NOTE`     => "$info"
                case `WARNING`  => "$warning"
                case `ERROR`    => "$error"
                case `FAILURE`  => "$fatal"
              }
              if(assertStatement.kind == AssertStatementKind.ASSERT) {
                b ++= s"${tab}$keyword($cond) else begin\n"
                b ++= s"""${tab}  $severity("$frontString"$backString);\n"""
                if (assertStatement.severity == `FAILURE`) b ++= tab + "  $finish;\n"
                b ++= s"${tab}end\n"
              }else{
                b ++= s"${tab}$keyword($cond);\n"
              }
            }

            b ++= s"`endif\n"
        }
        statementIndex += 1
      } else {

        var scopePtr = targetScope

        while(scopePtr.parentStatement != null && scopePtr.parentStatement.parentScope != scope){
          scopePtr = scopePtr.parentStatement.parentScope
        }

        if(scopePtr.parentStatement == null) {
          closeSubs()
          return statementIndex
        }

        val treeStatement = scopePtr.parentStatement

        if(treeStatement != lastWhen) {
          closeSubs()
        }

        treeStatement match {
          case treeStatement: WhenStatement =>
            if(scopePtr == treeStatement.whenTrue){
              b ++= s"${tab}if(${emitExpression(treeStatement.cond)})begin\n"
            } else if(lastWhen == treeStatement){
              //              if(scopePtr.sizeIsOne && scopePtr.head.isInstanceOf[WhenStatement]){
              //                b ++= s"${tab}if ${emitExpression(treeStatement.cond)} = '1' then\n"
              //              } else {
              b ++= s"${tab}end else begin\n"
              //              }
            } else {
              b ++= s"${tab}if(! ${emitExpression(treeStatement.cond)}) begin\n"
            }
            lastWhen = treeStatement
            statementIndex = emitLeafStatements(statements,statementIndex, scopePtr, assignmentKind,b, tab + "  ")
          case switchStatement : SwitchStatement =>
            val isPure = switchStatement.elements.foldLeft(true)((carry, element) => carry && !(element.keys.exists(!_.isInstanceOf[Literal])))
            //Generate the code
            def findSwitchScopeRec(scope: ScopeStatement): ScopeStatement = scope.parentStatement match {
              case null => null
              case s if s == switchStatement => scope
              case s => findSwitchScopeRec(s.parentScope)
            }

            def findSwitchScope() : ScopeStatement = {
              if(statementIndex < statements.length)
                findSwitchScopeRec(statements(statementIndex).parentScope)
              else
                null
            }

            var nextScope = findSwitchScope()

            if(isPure) {
              def emitIsCond(that: Expression): String = that match {
                case e: BitVectorLiteral => s"${e.getWidth}'b${e.getBitsStringOn(e.getWidth,'x')}"
                case e: BoolLiteral      => if(e.value) "1'b1" else "1'b0"
                case lit: EnumLiteral[_] => emitEnumLiteral(lit.enum, lit.encoding)
              }

              b ++= s"${tab}case(${emitExpression(switchStatement.value)})\n"

              switchStatement.elements.foreach(element => {
                b ++= s"${tab}  ${element.keys.map(e => emitIsCond(e)).mkString(", ")} : begin\n"
                if(nextScope == element.scopeStatement) {
                  statementIndex = emitLeafStatements(statements, statementIndex, element.scopeStatement, assignmentKind, b, tab + "    ")
                  nextScope = findSwitchScope()
                }
                b ++= s"${tab}  end\n"
              })

              b ++= s"${tab}  default : begin\n"

              if(nextScope == switchStatement.defaultScope) {
                statementIndex = emitLeafStatements(statements, statementIndex, switchStatement.defaultScope, assignmentKind, b, tab + "    ")
                nextScope = findSwitchScope()
              }

              b ++= s"${tab}  end\n"
              b ++= s"${tab}endcase\n"

            } else {

              def emitIsCond(that: Expression): String = that match {
                case that: SwitchStatementKeyBool => s"(${emitExpression(that.cond)})"
                case that                         => s"(${emitExpression(switchStatement.value)} == ${emitExpression(that)})"
              }

              var index = 0

              switchStatement.elements.foreach(element => {
                b ++= s"${tab}${if(index == 0) "if" else "end else if"}(${element.keys.map(e => emitIsCond(e)).mkString(" || ")}) begin\n"
                if(nextScope == element.scopeStatement) {
                  statementIndex = emitLeafStatements(statements, statementIndex, element.scopeStatement, assignmentKind, b, tab + "    ")
                  nextScope = findSwitchScope()
                }
                index += 1
              })

              if(switchStatement.defaultScope != null){
                b ++= s"${tab}end else begin\n"
                if(nextScope == switchStatement.defaultScope) {
                  statementIndex = emitLeafStatements(statements, statementIndex, switchStatement.defaultScope, assignmentKind, b, tab + "    ")
                  nextScope = findSwitchScope()
                }
              }
              b ++= s"${tab}end\n"
            }
        }
      }
    }
    closeSubs()

    return statementIndex
  }

  def referenceSetStart(): Unit ={
    _referenceSetEnabled = true
    _referenceSet.clear()
  }

  def referenceSetStop(): Unit ={
    _referenceSetEnabled = false
    _referenceSet.clear()
  }

  def referenceSetPause(): Unit ={
    _referenceSetEnabled = false
  }

  def referenceSetResume(): Unit ={
    _referenceSetEnabled = true
  }

  def referenceSetAdd(str : String): Unit ={
    if(_referenceSetEnabled) {
      _referenceSet.add(str)
    }
  }

  def referenceSetSorted() = _referenceSet

  var _referenceSetEnabled = false
  val _referenceSet        = mutable.LinkedHashSet[String]()

  def emitReference(that: DeclarationStatement, sensitive: Boolean): String ={
    val name = referencesOverrides.getOrElse(that, that.getNameElseThrow) match {
      case x : String               => x
      case x : DeclarationStatement => emitReference(x, false)
    }
    if(sensitive) referenceSetAdd(name)
    name
  }

  def emitReferenceNoOverrides(that : DeclarationStatement): String ={
    that.getNameElseThrow
  }

  def emitAssignedExpression(that : Expression): String = that match{
    case that: BaseType                 => emitReference(that, false)
    case that: BitAssignmentFixed       => s"${emitReference(that.out, false)}[${that.bitId}]"
    case that: BitAssignmentFloating    => s"${emitReference(that.out, false)}[${emitExpression(that.bitId)}]"
    case that: RangedAssignmentFixed    => s"${emitReference(that.out, false)}[${that.hi} : ${that.lo}]"
    case that: RangedAssignmentFloating => s"${emitReference(that.out, false)}[${emitExpression(that.offset)} +: ${that.bitCount}]"
  }

  def emitExpression(that: Expression): String = {
    wrappedExpressionToName.get(that) match {
      case Some(name) =>
        referenceSetAdd(name)
        name
      case None => dispatchExpression(that)
    }
  }

  def emitExpressionNoWrappeForFirstOne(that: Expression): String = dispatchExpression(that)

  def emitBaseTypeSignal(baseType: BaseType, name: String): String = {
    s"  ${emitSyntaxAttributes(baseType.instanceAttributes)}${if(signalNeedProcess(baseType)) "reg " else "wire "}${emitType(baseType)} ${name}${getBaseTypeSignalInitialisation(baseType)}${emitCommentAttributes(baseType.instanceAttributes)};\n"
  }

  def emitBaseTypeWrap(baseType: BaseType, name: String): String = {
    s"  ${if(signalNeedProcess(baseType)) "reg " else "wire "}${emitType(baseType)} ${name};\n"
  }

  def getBaseTypeSignalInitialisation(signal: BaseType): String = {
    if(signal.isReg){
      if(signal.clockDomain.config.resetKind == BOOT && signal.hasInit) {
        var initExpression: Literal = null
        var needFunc = false

        signal.foreachStatements {
          case s: InitAssignmentStatement =>
            assert(s.target == signal, s"Partial init not supported on $signal")
            if(initExpression != null)
              needFunc = true
            def findLiteral(that : Expression): Literal = that match{
              case that : Literal => that
              case that : BaseType => {
                if(Statement.isSomethingToFullStatement(that)){
                  findLiteral(that.head.source)
                }else{
                  SpinalError(s"Can't resolve the literal value of $that")
                }
              }
            }

            initExpression = s match {
              case s : Literal => s
              case _ => findLiteral(s.source)
            }
          case s =>
        }

        if(needFunc)
          ???
        else {
//          assert(initStatement.parentScope == signal.parentScope)
          return " = " + emitExpressionNoWrappeForFirstOne(initExpression)
        }
      }else if (signal.hasTag(randomBoot)) {
        return signal match {
          case b: Bool       =>
            " = " + (if (Random.nextBoolean()) "1" else "0")
          case bv: BitVector =>
            val rand = BigInt(bv.getWidth, Random).toString(2)
            " = " + bv.getWidth + "'b" + "0" * (bv.getWidth - rand.length) + rand
          case e: SpinalEnumCraft[_] =>
            val vec  = e.spinalEnum.elements.toVector
            val rand = vec(Random.nextInt(vec.size))
            " = " + emitEnumLiteral(rand, e.getEncoding)
        }
      }
    }
    ""
  }

  var memBitsMaskKind: MemBitsMaskKind = MULTIPLE_RAM

  def emitSignals(): Unit = {
    component.dslBody.walkDeclarations {
      case signal: BaseType =>
        if (!signal.isIo) {
          declarations ++= emitBaseTypeSignal(signal, emitReference(signal, false))
        }
      case mem: Mem[_] =>
    }
  }

  def emitMems(mems: ArrayBuffer[Mem[_]]): Unit = {
    for(mem <- mems) emitMem(mem)
  }

  var verilogIndexGenerated = false

  def emitMem(mem: Mem[_]): Unit ={

    def emitDataType(mem: Mem[_], constrained: Boolean = true) =  s"${emitReference(mem, constrained)}_type"

    //ret ++= emitSignal(mem, mem);
    val symbolWidth = mem.getMemSymbolWidth()
    val symbolCount = mem.getMemSymbolCount()

    val initAssignmentBuilder = for(i <- 0 until symbolCount) yield {
      val builder = new StringBuilder()
      val mask    = (BigInt(1) << symbolWidth) - 1

      if (mem.initialContent != null) {
        builder ++= " := ("

        var first = true
        for ((value, index) <- mem.initialContent.zipWithIndex) {
          if (!first)
            builder ++= ","
          else
            first = false

          if ((index & 15) == 0) {
            builder ++= "\n     "
          }

          val unfilledValue = ((value>>(i*symbolWidth)) & mask).toString(2)
          val filledValue   = "0" * (symbolWidth-unfilledValue.length) + unfilledValue
          builder ++= "\"" + filledValue + "\""
        }

        builder ++= ")"

      }else if(mem.hasTag(randomBoot)){
        builder ++= " := (others => (others => '1'))"
      }
      builder
    }

    if(memBitsMaskKind == MULTIPLE_RAM && symbolCount != 1) {
      for(i <- 0 until symbolCount) {
          val postfix = "_symbol" + i
        declarations ++= s"  ${emitSyntaxAttributes(mem.instanceAttributes(Language.VERILOG))}reg [${symbolWidth- 1}:0] ${emitReference(mem,false)}$postfix [0:${mem.wordCount - 1}]${emitCommentAttributes(mem.instanceAttributes(Language.VERILOG))};\n"
      }
    }else{
      declarations ++= s"  ${emitSyntaxAttributes(mem.instanceAttributes(Language.VERILOG))}reg ${emitRange(mem)} ${emitReference(mem,false)} [0:${mem.wordCount - 1}]${emitCommentAttributes(mem.instanceAttributes(Language.VERILOG))};\n"
    }

    if (mem.initialContent != null) {
      logics ++= "  initial begin\n"
      if(nativeRom) {
        for ((value, index) <- mem.initialContent.zipWithIndex) {
          val unfilledValue = value.toString(2)
          val filledValue = "0" * (mem.getWidth - unfilledValue.length) + unfilledValue
          if (memBitsMaskKind == MULTIPLE_RAM && symbolCount != 1) {
            for (i <- 0 until symbolCount) {
              logics ++= s"    ${emitReference(mem, false)}_symbol$i[$index] = 'b${filledValue.substring(symbolWidth * (symbolCount - i - 1), symbolWidth * (symbolCount - i))};\n"
            }
          } else {
            logics ++= s"    ${emitReference(mem, false)}[$index] = ${filledValue.length}'b$filledValue;\n"
          }
        }
      }else {
        val filePath = s"${nativeRomFilePrefix}_${(component.parents() :+ component).map(_.getName()).mkString("_")}_${emitReference(mem, false)}"
        val relativePath = new File(filePath).getName
        if (memBitsMaskKind == MULTIPLE_RAM && symbolCount != 1) {
          for (i <- 0 until symbolCount) {
            logics ++= s"""    $$readmemb("${relativePath}_symbol$i.bin",${emitReference(mem, false)}_symbol$i);\n"""
          }
        } else {
          logics ++= s"""    $$readmemb("${relativePath}.bin",${emitReference(mem, false)});\n"""
        }

        val files = if (memBitsMaskKind == MULTIPLE_RAM && symbolCount != 1) {
          List.tabulate(symbolCount){i => {
            val name = s"${filePath}_symbol$i.bin"
            emitedRtlSourcesPath += name
            new java.io.FileWriter(name)
          }}
        }else{
          emitedRtlSourcesPath += s"${filePath}.bin"
          List(new java.io.FileWriter(s"${filePath}.bin"))
        }
        for ((value, index) <- mem.initialContent.zipWithIndex) {
          val unfilledValue = value.toString(2)
          val filledValue = "0" * (mem.getWidth - unfilledValue.length) + unfilledValue
          if (memBitsMaskKind == MULTIPLE_RAM && symbolCount != 1) {
            for (i <- 0 until symbolCount) {
              files(i).write( s"${filledValue.substring(symbolWidth * (symbolCount - i - 1), symbolWidth * (symbolCount - i))}\n")
            }
          } else {
            files.head.write( s"$filledValue\n")
          }
        }

        files.foreach(_.flush())
        files.foreach(_.close())
      }

      logics ++= "  end\n"
    }else if(mem.hasTag(randomBoot)){
      if(!verilogIndexGenerated) {
        verilogIndexGenerated = true
        logics ++= "integer verilogIndex;\n"
      }
      logics ++= s"""
initial begin
  for (verilogIndex = 0; verilogIndex < ${mem.wordCount}; verilogIndex = verilogIndex + 1)begin
${
  if(symbolCount == 1){
    emitReference(mem,false) + "[verilogIndex] = -1;"
  }  else {
    (0 until symbolCount).map("    " + emitReference(mem,false)  + "_symbol" + _ + "[verilogIndex] = -1;").reduce(_ + "\n" +_)
  }}
  end
end
"""
    }

    def emitWrite(b: StringBuilder, mem: Mem[_], writeEnable: String, address: Expression, data: Expression, mask: Expression with WidthProvider, symbolCount: Int, bitPerSymbole: Int, tab: String): Unit = {

      if(memBitsMaskKind == SINGLE_RAM || symbolCount == 1) {
        val ramAssign = s"$tab${emitReference(mem, false)}[${emitExpression(address)}] <= ${emitExpression(data)};\n"

        if (writeEnable != null) {
          b ++= s"${tab}if(${writeEnable}) begin\n  "
          b ++= ramAssign
          b ++= s"${tab}end\n"
        } else {
          b ++= ramAssign
        }

      } else {

        def maskCount = mask.getWidth

        for(i <- 0 until symbolCount) {
          var conds = if(writeEnable != null) List(writeEnable) else Nil
          val range = s"[${(i + 1) * bitPerSymbole - 1} : ${i * bitPerSymbole}]"

          if(mask != null)
            conds =  s"${emitExpression(mask)}[$i]" :: conds

          if(conds.nonEmpty)
            b ++= s"${tab}if(${conds.mkString(" && ")}) begin\n"

          if (memBitsMaskKind == SINGLE_RAM || symbolCount == 1)
            b ++= s"$tab  ${emitReference(mem, false)}[${emitExpression(address)}]$range <= ${emitExpression(data)}$range;\n"
          else
            b ++= s"$tab  ${emitReference(mem, false)}_symbol${i}[${emitExpression(address)}] <= ${emitExpression(data)}$range;\n"

          if(conds.nonEmpty)
            b ++= s"${tab}end\n"
        }
      }
    }

    def emitRead(b: StringBuilder, mem: Mem[_], address: Expression, target: Expression, tab: String) = {
      val ramRead = {
        val symbolCount = mem.getMemSymbolCount()

        if(memBitsMaskKind == SINGLE_RAM || symbolCount == 1)
          b ++= s"$tab${emitExpression(target)} <= ${emitReference(mem, false)}[${emitExpression(address)}];\n"
        else{
          val symboleReadDataNames = for(i <- 0 until symbolCount) yield {
            val symboleReadDataName = component.localNamingScope.allocateName(anonymSignalPrefix)
            declarations ++= s"  reg [${mem.getMemSymbolWidth()-1}:0] $symboleReadDataName;\n"
            b ++= s"$tab$symboleReadDataName <= ${emitReference(mem,false)}_symbol$i[${emitExpression(address)}];\n"
            symboleReadDataName
          }

//          logics ++= s"  always @ (${symboleReadDataNames.mkString(" or " )}) begin\n"
          logics ++= s"  always @ (*) begin\n"
          logics ++= s"    ${emitExpression(target)} = {${symboleReadDataNames.reverse.mkString(", " )}};\n"
          logics ++= s"  end\n"
        }
        //          (0 until symbolCount).reverse.map(i => (s"${emitReference(mem, false)}_symbol$i(to_integer(${emitExpression(address)}))")).reduce(_ + " & " + _)
      }
    }

    def emitPort(port: MemPortStatement, tab: String, b: mutable.StringBuilder): Unit = port match {
      case memWrite: MemWrite =>
        if(memWrite.aspectRatio != 1) SpinalError(s"Verilog backend can't emit ${memWrite.mem} because of its mixed width ports")
        emitWrite(b, memWrite.mem,  if (memWrite.writeEnable != null) emitExpression(memWrite.writeEnable) else null.asInstanceOf[String], memWrite.address, memWrite.data, memWrite.mask, memWrite.mem.getMemSymbolCount(), memWrite.mem.getMemSymbolWidth(), tab)
      case memReadSync: MemReadSync =>
        if(memReadSync.aspectRatio != 1) SpinalError(s"Verilog backend can't emit ${memReadSync.mem} because of its mixed width ports")
        if(memReadSync.readUnderWrite == writeFirst) SpinalError(s"Can't translate a memReadSync with writeFirst into Verilog $memReadSync")
        if(memReadSync.readUnderWrite == dontCare) SpinalWarning(s"memReadSync with dontCare is as readFirst into Verilog $memReadSync")
        if(memReadSync.readEnable != null) {
          b ++= s"${tab}if(${emitExpression(memReadSync.readEnable)}) begin\n"
          emitRead(b, memReadSync.mem, memReadSync.address, memReadSync, tab + "  ")
          b ++= s"${tab}end\n"
        } else {
          emitRead(b, memReadSync.mem, memReadSync.address, memReadSync, tab)
        }
      case memReadWrite: MemReadWrite =>
        if(memReadWrite.aspectRatio != 1) SpinalError(s"Verilog backend can't emit ${memReadWrite.mem} because of its mixed width ports")
        //                    if (memReadWrite.readUnderWrite == writeFirst) SpinalError(s"Can't translate a MemWriteOrRead with writeFirst into VERILOG $memReadWrite")
        //                    if (memReadWrite.readUnderWrite == dontCare) SpinalWarning(s"MemWriteOrRead with dontCare is as readFirst into VERILOG $memReadWrite")

        val symbolCount = memReadWrite.mem.getMemSymbolCount()
        emitWrite(b, memReadWrite.mem,s"${emitExpression(memReadWrite.chipSelect)} && ${emitExpression(memReadWrite.writeEnable)} ", memReadWrite.address, memReadWrite.data, memReadWrite.mask, memReadWrite.mem.getMemSymbolCount(), memReadWrite.mem.getMemSymbolWidth(),tab)
        b ++= s"${tab}if(${emitExpression(memReadWrite.chipSelect)}) begin\n"
        emitRead(b, memReadWrite.mem, memReadWrite.address, memReadWrite, tab + "  ")
        b ++= s"${tab}end\n"
    }

    val cdTasks = mutable.LinkedHashMap[ClockDomain, ArrayBuffer[MemPortStatement]]()
    mem.foreachStatements{
      case port: MemWrite     =>
        cdTasks.getOrElseUpdate(port.clockDomain, ArrayBuffer[MemPortStatement]()) += port
      case port: MemReadSync  =>
        if(port.readUnderWrite == readFirst)
          cdTasks.getOrElseUpdate(port.clockDomain, ArrayBuffer[MemPortStatement]()) += port
      case port: MemReadWrite =>
        cdTasks.getOrElseUpdate(port.clockDomain, ArrayBuffer[MemPortStatement]()) += port
      case port: MemReadAsync =>
    }

    val tmpBuilder = new StringBuilder()

    for((cd, ports) <- cdTasks){
      def syncLogic(tab: String, b: StringBuilder): Unit ={
        ports.foreach{
          case port: MemWrite     => emitPort(port, tab, b)
          case port: MemReadSync  => emitPort(port, tab, b)
          case port: MemReadWrite => emitPort(port, tab, b)
        }
      }
      emitClockedProcess(syncLogic, null, tmpBuilder, cd, false)
    }

    mem.foreachStatements{
      case port: MemWrite      =>
      case port: MemReadWrite  =>
      case port: MemReadSync   =>
        if(port.readUnderWrite == dontCare)
          emitClockedProcess(emitPort(port, _, _), null, tmpBuilder, port.clockDomain, false)
      case port: MemReadAsync  =>
        if(port.aspectRatio != 1) SpinalError(s"VERILOG backend can't emit ${port.mem} because of its mixed width ports")

        if (port.readUnderWrite == dontCare) SpinalWarning(s"memReadAsync with dontCare is as writeFirst into VERILOG")

        val symbolCount = port.mem.getMemSymbolCount()

        if(memBitsMaskKind == SINGLE_RAM || symbolCount == 1)
          tmpBuilder ++= s"  assign ${emitExpression(port)} = ${emitReference(port.mem, false)}[${emitExpression(port.address)}];\n"
        else
          (0 until symbolCount).foreach(i => tmpBuilder  ++= s"  assign ${emitExpression(port)}[${(i + 1) * symbolWidth - 1} : ${i * symbolWidth}] = ${emitReference(port.mem, false)}_symbol$i[${emitExpression(port.address)}];\n")

    }

    logics ++= tmpBuilder
  }

  def fillExpressionToWrap(): Unit = {

    def applyTo(that: Expression) = expressionToWrap += that

    def onEachExpression(e: Expression): Unit = {
      e match {
        case node: SubAccess => applyTo(node.getBitVector)
        case node: Resize => applyTo(node.input)
        case _               =>
      }
    }

    def onEachExpressionNotDrivingBaseType(e: Expression): Unit = {
      onEachExpression(e)
      e match {
    //    case node: Literal => applyTo(node)
        case node: Resize                           => applyTo(node)
        case node if node.getTypeObject == TypeSInt => applyTo(node)
        case node: Operator.UInt.Add                => applyTo(node)
        case node: Operator.UInt.Sub                => applyTo(node)
        case node: Operator.UInt.Mul                => applyTo(node)
        case node: Operator.UInt.Div                => applyTo(node)
        case node: Operator.UInt.Mod                => applyTo(node)
        case node: Operator.BitVector.ShiftOperator => applyTo(node)
        case _ =>
      }
    }
    component.dslBody.walkStatements{
      case s: AssignmentStatement =>
        s.foreachExpression(e => {
          onEachExpression(e)
          e.walkDrivingExpressions(onEachExpressionNotDrivingBaseType)
        })
      case s => s.walkDrivingExpressions(onEachExpressionNotDrivingBaseType)
    }
  }

  def refImpl(e: BaseType): String = emitReference(e, true)

  def operatorImplAsBinaryOperator(verilog: String)(e: BinaryOperator): String = {
    s"(${emitExpression(e.left)} $verilog ${emitExpression(e.right)})"
  }

  def operatorImplAsBinaryOperatorSigned(vhd: String)(op: BinaryOperator): String = {
    s"($$signed(${emitExpression(op.left)}) $vhd $$signed(${emitExpression(op.right)}))"
  }

  def operatorImplAsBinaryOperatorLeftSigned(vhd: String)(op: BinaryOperator): String = {
    s"($$signed(${emitExpression(op.left)}) $vhd ${emitExpression(op.right)})"
  }

  def boolLiteralImpl(e: BoolLiteral) : String = if(e.value) "1'b1" else "1'b0"

  def operatorImplAsUnaryOperator(verilog: String)(e: UnaryOperator): String = {
    s"($verilog ${emitExpression(e.source)})"
  }

  def operatorImplAsMux(e: BinaryMultiplexer): String = {
    s"(${emitExpression(e.cond)} ? ${emitExpression(e.whenTrue)} : ${emitExpression(e.whenFalse)})"
  }

  def shiftRightSignedByIntFixedWidthImpl(e: Operator.BitVector.ShiftRightByIntFixedWidth): String = {
    s"($$signed(${emitExpression(e.source)}) >>> ${e.shift})"
  }

  def operatorImplAsCat(e: Operator.Bits.Cat): String = {
    s"{${emitExpression(e.left)},${emitExpression(e.right)}}"
  }

  def operatorImplAsNoTransformation(func: Cast): String = {
    emitExpression(func.input)
  }

  def operatorImplResize(func: Resize): String = {
    if(func.size < func.input.getWidth)
      s"${emitExpression(func.input)}[${func.size-1}:0]"
    else if(func.size > func.input.getWidth)
      s"{${func.size - func.input.getWidth}'d0, ${emitExpression(func.input)}}"
    else
      emitExpression(func.input)
  }

  def operatorImplResizeSigned(func: Resize): String = {
    if(func.size < func.input.getWidth)
      s"${emitExpression(func.input)}[${func.size-1}:0]"
    else if(func.size > func.input.getWidth)
      s"{{${func.size - func.input.getWidth}{${emitExpression(func.input)}[${func.input.getWidth-1}]}}, ${emitExpression(func.input)}}"
    else
      emitExpression(func.input)
  }

  def shiftRightByIntImpl(e: Operator.BitVector.ShiftRightByInt): String = {
    s"(${emitExpression(e.source)} >>> ${e.shift})"
  }

  def shiftLeftByIntImpl(e: Operator.BitVector.ShiftLeftByInt): String = {
    s"({${e.shift}'d0,${emitExpression(e.source)}} <<< ${e.shift})"
  }


  def shiftLeftByUIntImpl(e: Operator.BitVector.ShiftLeftByUInt): String = {
    s"({${e.getWidth-e.left.getWidth}'d0,${emitExpression(e.left)}} <<< ${emitExpression(e.right)})"
  }
  def shiftLeftByUIntImplSigned(e: Operator.SInt.ShiftLeftByUInt): String = {
    s"({{${e.getWidth - e.left.getWidth}{${emitExpression(e.left)}[${e.left.getWidth-1}]}},${emitExpression(e.left)}} <<< ${emitExpression(e.right)})"
  }

  def shiftRightByIntFixedWidthImpl(e: Operator.BitVector.ShiftRightByIntFixedWidth): String = {
    s"(${emitExpression(e.source)} >>> ${e.shift})"
  }

  def shiftLeftByIntFixedWidthImpl(e: Operator.BitVector.ShiftLeftByIntFixedWidth): String = {
    s"(${emitExpression(e.source)} <<< ${e.shift})"
  }

  def emitBitVectorLiteral(e: BitVectorLiteral): String = {
    s"(${e.getWidth}'b${e.getBitsStringOn(e.getWidth,'x')})"
  }

  def emitEnumLiteralWrap(e: EnumLiteral[_  <: SpinalEnum]): String = {
    emitEnumLiteral(e.enum, e.encoding)
  }

  def enumEgualsImpl(eguals: Boolean)(e: BinaryOperator with EnumEncoded): String = {
    val enumDef  = e.getDefinition
    val encoding = e.getEncoding

    encoding match {
      case `binaryOneHot` => s"((${emitExpression(e.left)} & ${emitExpression(e.right)}) ${if (eguals) "!=" else "=="} ${encoding.getWidth(enumDef)}'b${"0" * encoding.getWidth(enumDef)})"
      case _              => s"(${emitExpression(e.left)} ${if (eguals) "==" else "!="} ${emitExpression(e.right)})"
    }
  }

  def operatorImplAsEnumToEnum(e: CastEnumToEnum): String = {
    val enumDefSrc  = e.input.getDefinition
    val encodingSrc = e.input.getEncoding
    val enumDefDst  = e.getDefinition
    val encodingDst = e.getEncoding

    s"${getReEncodingFuntion(enumDefDst, encodingSrc,encodingDst)}(${emitExpression(e.input)})"
  }

  def emitEnumPoison(e: EnumPoison): String = {
    val width = e.encoding.getWidth(e.enum)
    s"(${width}'${"x" * width})"
  }

  def accessBoolFixed(e: BitVectorBitAccessFixed): String = {
    s"${emitExpression(e.source)}[${e.bitId}]"
  }

  def accessBoolFloating(e: BitVectorBitAccessFloating): String = {
    s"${emitExpression(e.source)}[${emitExpression(e.bitId)}]"
  }

  def accessBitVectorFixed(e: BitVectorRangedAccessFixed): String = {
    s"${emitExpression(e.source)}[${e.hi} : ${e.lo}]"
  }

  def accessBitVectorFloating(e: BitVectorRangedAccessFloating): String = {
    s"${emitExpression(e.source)}[${emitExpression(e.offset)} +: ${e.size}]"
  }

  def dispatchExpression(e: Expression): String = e match {
    case  e: BaseType                                 => refImpl(e)

    case  e: BoolLiteral                              => boolLiteralImpl(e)
    case  e: BitVectorLiteral                         => emitBitVectorLiteral(e)
    case  e: EnumLiteral[_]                           => emitEnumLiteralWrap(e)

    case  e: BoolPoison                               => "1'bx"
    case  e: EnumPoison                               => emitEnumPoison(e)

    //unsigned
    case  e: Operator.UInt.Add                        => operatorImplAsBinaryOperator("+")(e)
    case  e: Operator.UInt.Sub                        => operatorImplAsBinaryOperator("-")(e)
    case  e: Operator.UInt.Mul                        => operatorImplAsBinaryOperator("*")(e)
    case  e: Operator.UInt.Div                        => operatorImplAsBinaryOperator("/")(e)
    case  e: Operator.UInt.Mod                        => operatorImplAsBinaryOperator("%")(e)

    case  e: Operator.UInt.Or                         => operatorImplAsBinaryOperator("|")(e)
    case  e: Operator.UInt.And                        => operatorImplAsBinaryOperator("&")(e)
    case  e: Operator.UInt.Xor                        => operatorImplAsBinaryOperator("^")(e)
    case  e: Operator.UInt.Not                        =>  operatorImplAsUnaryOperator("~")(e)

    case  e: Operator.UInt.Equal                      => operatorImplAsBinaryOperator("==")(e)
    case  e: Operator.UInt.NotEqual                   => operatorImplAsBinaryOperator("!=")(e)
    case  e: Operator.UInt.Smaller                    =>  operatorImplAsBinaryOperator("<")(e)
    case  e: Operator.UInt.SmallerOrEqual             => operatorImplAsBinaryOperator("<=")(e)

    case  e: Operator.UInt.ShiftRightByInt            => shiftRightByIntImpl(e)
    case  e: Operator.UInt.ShiftLeftByInt             => shiftLeftByIntImpl(e)
    case  e: Operator.UInt.ShiftRightByUInt           => operatorImplAsBinaryOperator(">>>")(e)
    case  e: Operator.UInt.ShiftLeftByUInt            => shiftLeftByUIntImpl(e)
    case  e: Operator.UInt.ShiftRightByIntFixedWidth  =>  shiftRightByIntFixedWidthImpl(e)
    case  e: Operator.UInt.ShiftLeftByIntFixedWidth   =>  shiftLeftByIntFixedWidthImpl(e)
    case  e: Operator.UInt.ShiftLeftByUIntFixedWidth  =>  operatorImplAsBinaryOperator("<<<")(e)

    //signed
    case  e: Operator.SInt.Add                        => operatorImplAsBinaryOperatorSigned("+")(e)
    case  e: Operator.SInt.Sub                        => operatorImplAsBinaryOperatorSigned("-")(e)
    case  e: Operator.SInt.Mul                        => operatorImplAsBinaryOperatorSigned("*")(e)
    case  e: Operator.SInt.Div                        => operatorImplAsBinaryOperatorSigned("/")(e)
    case  e: Operator.SInt.Mod                        => operatorImplAsBinaryOperatorSigned("%")(e)

    case  e: Operator.SInt.Or                         => operatorImplAsBinaryOperator("|")(e)
    case  e: Operator.SInt.And                        => operatorImplAsBinaryOperator("&")(e)
    case  e: Operator.SInt.Xor                        => operatorImplAsBinaryOperator("^")(e)
    case  e: Operator.SInt.Not                        =>  operatorImplAsUnaryOperator("~")(e)
    case  e: Operator.SInt.Minus                      => operatorImplAsUnaryOperator("-")(e)

    case  e: Operator.SInt.Equal                      => operatorImplAsBinaryOperatorSigned("==")(e)
    case  e: Operator.SInt.NotEqual                   => operatorImplAsBinaryOperatorSigned("!=")(e)
    case  e: Operator.SInt.Smaller                    =>  operatorImplAsBinaryOperatorSigned("<")(e)
    case  e: Operator.SInt.SmallerOrEqual             => operatorImplAsBinaryOperatorSigned("<=")(e)

    case  e: Operator.SInt.ShiftRightByInt            => shiftRightByIntImpl(e)
    case  e: Operator.SInt.ShiftLeftByInt             => shiftLeftByIntImpl(e)
    case  e: Operator.SInt.ShiftRightByUInt           => operatorImplAsBinaryOperatorLeftSigned(">>>")(e)
    case  e: Operator.SInt.ShiftLeftByUInt            =>  shiftLeftByUIntImplSigned(e)
    case  e: Operator.SInt.ShiftRightByIntFixedWidth  =>  shiftRightSignedByIntFixedWidthImpl(e)
    case  e: Operator.SInt.ShiftLeftByIntFixedWidth   =>  shiftLeftByIntFixedWidthImpl(e)
    case  e: Operator.SInt.ShiftLeftByUIntFixedWidth  =>  operatorImplAsBinaryOperatorLeftSigned("<<<")(e)

    //bits
    case  e: Operator.Bits.Cat                        => operatorImplAsCat(e)
    case  e: Operator.Bits.Or                         => operatorImplAsBinaryOperator("|")(e)
    case  e: Operator.Bits.And                        => operatorImplAsBinaryOperator("&")(e)
    case  e: Operator.Bits.Xor                        => operatorImplAsBinaryOperator("^")(e)
    case  e: Operator.Bits.Not                        =>  operatorImplAsUnaryOperator("~")(e)
    case  e: Operator.Bits.Equal                      => operatorImplAsBinaryOperator("==")(e)
    case  e: Operator.Bits.NotEqual                   => operatorImplAsBinaryOperator("!=")(e)

    case  e: Operator.Bits.ShiftRightByInt            => shiftRightByIntImpl(e)
    case  e: Operator.Bits.ShiftLeftByInt             => shiftLeftByIntImpl(e)
    case  e: Operator.Bits.ShiftRightByUInt           => operatorImplAsBinaryOperator(">>>")(e)
    case  e: Operator.Bits.ShiftLeftByUInt            =>  shiftLeftByUIntImpl(e)
    case  e: Operator.Bits.ShiftRightByIntFixedWidth  =>  shiftRightByIntFixedWidthImpl(e)
    case  e: Operator.Bits.ShiftLeftByIntFixedWidth   =>  shiftLeftByIntFixedWidthImpl(e)
    case  e: Operator.Bits.ShiftLeftByUIntFixedWidth  =>  operatorImplAsBinaryOperator("<<<")(e)

    //bool
    case  e: Operator.Bool.Equal                      => operatorImplAsBinaryOperator("==")(e)
    case  e: Operator.Bool.NotEqual                   => operatorImplAsBinaryOperator("!=")(e)

    case  e: Operator.Bool.Not                        => operatorImplAsUnaryOperator("!")(e)
    case  e: Operator.Bool.And                        => operatorImplAsBinaryOperator("&&")(e)
    case  e: Operator.Bool.Or                         => operatorImplAsBinaryOperator("||")(e)
    case  e: Operator.Bool.Xor                        => operatorImplAsBinaryOperator("^")(e)

    //enum
    case  e: Operator.Enum.Equal                      => enumEgualsImpl(true)(e)
    case  e: Operator.Enum.NotEqual                   => enumEgualsImpl(false)(e)

    //cast
    case  e: CastSIntToBits                           => operatorImplAsNoTransformation(e)
    case  e: CastUIntToBits                           => operatorImplAsNoTransformation(e)
    case  e: CastBoolToBits                           => operatorImplAsNoTransformation(e)
    case  e: CastEnumToBits                           => operatorImplAsNoTransformation(e)

    case  e: CastBitsToSInt                           => operatorImplAsNoTransformation(e)
    case  e: CastUIntToSInt                           => operatorImplAsNoTransformation(e)

    case  e: CastBitsToUInt                           => operatorImplAsNoTransformation(e)
    case  e: CastSIntToUInt                           => operatorImplAsNoTransformation(e)

    case  e: CastBitsToEnum                           => operatorImplAsNoTransformation(e)
    case  e: CastEnumToEnum                           => operatorImplAsEnumToEnum(e)

    //misc
    case  e: ResizeSInt                               => operatorImplResizeSigned(e)
    case  e: ResizeUInt                               => operatorImplResize(e)
    case  e: ResizeBits                               => operatorImplResize(e)

    case  e: BinaryMultiplexer                        => operatorImplAsMux(e)

    case  e: BitVectorBitAccessFixed                  => accessBoolFixed(e)
    case  e: BitVectorBitAccessFloating               => accessBoolFloating(e)
    case  e: BitVectorRangedAccessFixed               => accessBitVectorFixed(e)
    case  e: BitVectorRangedAccessFloating            => accessBitVectorFloating(e)
  }

  elaborate()
  fillExpressionToWrap()
  emitEntity()
  emitArchitecture()
}
