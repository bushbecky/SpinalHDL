/*
 * SpinalHDL
 * Copyright (c) Dolu, All rights reserved.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3.0 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.
 */

package spinal.test

import spinal.IntBuilder._
import spinal._

import scala.collection.mutable

/**
 * Created by PIC18F on 22.08.2014.
 */
object Try {
 /* object ttttttt{
    class Data {
      type Self <: Data
      def :=(that: Self) = println(2)
    }
    class Flow[T <: Data](val gen: T){
      def <<(that: Flow[gen.Self]) = {
        this.gen := that.gen         //that.gen  is marked as read (type mismatch, expected Flow.this.gen.Self, actual that.gen.Self
      }
    }

   // new Flow(new Data) << new Flow(new Data)
  }*/
//  abstract class Data{
//    type Self <: Data
//    def :=(that: Self)
//    def :==[S <: Self](that: S)
//  }
//
//  class Bits extends Data {
//    override type Self = Bits
//    override def :==[S <: Bits](that: S): Unit =  println(s"$this := $that")
//    override def :=(that: Self): Unit = println(s"$this := $that")
//  }
//
//  class UInt extends Data {
//    override type Self = UInt
//    override def :==[S <: UInt](that: S): Unit =  println(s"$this := $that")
//    override def :=(that: Self): Unit = println(s"$this := $that")
//  }
//
//  class Flow[T <: Data](gen: T) {
//    def <<(that: Flow[T]) = {
//      this.gen :== that.gen
//    }
//  }
//
//  new Bits() :==  new Bits()
//  val f1, f2 = new Flow(new Bits)
//  f1 << f2

  //import spinal.IntBuilder._
  //val b = in Bits(3 bit)
  /*class ComponentAAA extends Component {
    val io = new Bool
    io.asOutput
  }

  class ComponentAA extends Component {
    val io = new Bundle {
      val e = new Bool().asInput
      val f = new Bool().asOutput
      val g = new Bool().asOutput
    }
    io.g := io.e
    io.f := io.g
  }*/

  /*
    class ComponentA extends Component {
      val io = new Bundle {
        val a = new Bool().asInput
        val b = new Bool().asOutput
        val x = new UInt().asInput
        val y = new UInt().asInput
        val z = new UInt().asOutput
      }

      io.x.setWidth(5)
      io.y.setWidth(3)

      io.z := io.x + io.y

      val componentAA = Component(new ComponentAA)


      val c = new Bool
      val d = new Bool


      c := io.a
      d := c
      componentAA.io.e := d
      val opAnd = io.a && componentAA.io.f
      io.b := opAnd
    }
  */

  class ComponentAAA extends Component {
    val io = new Bundle {
      val in = (new Bool().asInput)
      val out = new Bool().asOutput
    }

    val r1 = (io.in.clone);
    val tempSignal = !r1
    val tempSignal2 = !tempSignal
    when(io.in) {
      r1 := RegNext(r1 && tempSignal2)
    } otherwise {
      r1 := Bool(true)
    }
    io.out := r1


  }

  class ComponentAA extends Component {
    val io = new Bundle {
      val in = (new Bool()).asInput
      val out = new Bool().asOutput
    }
    val AAA = Component(new ComponentAAA)
    AAA.io.in := io.in


    val c1 = io.in.clone;
    c1 := io.in
    val c2 = io.in.clone;
    c2 := io.in
    val c3 = io.in.clone;
    c3 := io.in
    val c4 = io.in.clone;
    c4 := io.in
    val c5 = io.in.clone;
    c5 := io.in
    val v1 = io.in.clone;
    v1 := io.in
    val v2 = io.in.clone;
    v2 := io.in
    val v3 = io.in.clone;
    v3 := io.in
    val v4 = io.in.clone;
    v4 := io.in
    val v5 = io.in.clone;
    v5 := io.in

    val res = RegInit(Bool(true))


    nameElements()
    res := v1
    when(c1) {
      when(c2) {
        res := v2
      }.elsewhen(c3) {
        res := v3
      }.elsewhen(c4) {
        res := v4
      }
    } otherwise {

      switch(c5) {
        is(v5 && v1) {
          res := v5 && v1
        }
        is(v5 && v2) {
          res := v5 && v2
          when(v5 && v4) {
            res := v5 && v4
          }
        }
        is(v5 && v3) {
          res := v5 && v3
        }
        is((!v1) :: (!v2) :: (!v3) :: Nil) {
          res := v5 && v5
        }
      }

    }
    io.out := AAA.io.out && res

  }

  class ComponentABA extends Component {
    val io = new Bundle {
      val in = new Bool().asInput
      val out = new Bool().asOutput
    }
    // io.out := ClockDomain.current.clock
    val temp = io.in.clone
    temp := io.in
    io.out := temp
  }

  class ComponentAB extends Component {
    val io = new Bundle {
      val in = new Bool().asInput
      val out = new Bool().asOutput
    }
    val ABA = Component(new ComponentABA)
    ABA.io.in := io.in
    io.out := ABA.io.out
  }

  class BundleAA extends BundleA {
    val a = new Bool()
    val d = new Bool()
    val e = MyEnum.craft()
  }


  class BundleA extends Bundle {
    val b = new Bool()
    val c = UInt(8 bit)
  }

  class RecursiveComponent(n: Int) extends Component {
    val io = new Bundle {
      val input = (in.Bool())
      val output = (out.Bool())
    }

    val subComponent = (0 until n).map(i => {
      val c = Component(new RecursiveComponent(n - 1))
      c.io.input := io.input
      c
    })


    io.output := subComponent.foldLeft(io.input)(_ && _.io.output)

  }

  class VecA extends Vec(new BundleAA()) {
    addE
    addE
    addE
    addE
    def addE = {
      val e = new BundleAA()

      addElement(e)
    }
  }

  /*
    import obj._

    object obj {
      implicit def IntToUInt(value: Int) = UInt(value)
     /// implicit def dd(value: Int) = UInt(value).toSInt
    }*/


  object u {
    def °(b: Int) = UInt(3)


    def apply() = true
  }


  class MyBlackBox extends BlackBox {
    val generic = new Bundle {
      val genA = UInt(1, 5 bit)
      val genB = Bool(false)
      val genC = Number(44)
      val genD = SString("salut")
    }

    val io = new Bundle {
      val inUIntA = in UInt (5 bit)
      val outUIntA = out UInt (5 bit)
    }
  }

  object S {
    def one = UInt(1)
    def two = one + one
  }

  class ComponentS extends Component {
    val io = new Bundle {
      val outUIntS = out UInt (5 bit)
    }
    io.outUIntS := S.two
  }

  object MyEnum extends SpinalEnum {
    val s0, s1, s2 = Value
  }
  object MyEnum2 extends SpinalEnum {
    val e0, e1, e2 = Value
  }

  class ComponentA extends Component {

    val io = new Bundle {
      val myClock = new Bool().asInput
      val myReset = new Bool().asInput
      //   val in = new Bool().asInput
      val cond0 = in.Bool()
      val cond1 = new Bool().asInput
      val cond2 = new Bool().asInput
      val cond3 = new Bool().asInput
      val cond4 = new Bool().asInput
      val cond5 = new Bool().asInput
      val inu4b = in UInt (4 bit)
      val inu8b = in UInt (8 bit)
     // val c = in UInt (5 bit)
    //  val d = in UInt (5 bit)
      //  val o = out UInt (5 bit)

      val outu = Reg(out.UInt())

      //      val default = new UInt().asInput
      //      // val outSInt = new SInt().asOutput
      //      //   val out = new UInt().asOutput
      //      //   val out2 = new UInt().asOutput
      val outBool = new Bool().asOutput
      val outBool2 = new Bool().asOutput
      //
      //
      val inBundle0 = new BundleAA().asInput
      val inBundle1 = new BundleA().asInput
      val inBundle2 = new BundleAA().asInput
      val inBundle3 = new BundleA().asInput
      val outBundleAA = new BundleAA().asOutput
      val outBundleA = new BundleA().asOutput
      //      //   val outBits = new Bits().asOutput
      //
      val inVec = new VecA().asInput
      //      //  val outVec = new VecA().asOutput
      //
      //
      // val outBool = out.Bool.apply()
      //      //  val outBool = out.Bool()
      //
      //      // val outUInt = out.UInt (0)
      //
      val inVecU = in(Vec.tabulate(4)(i => UInt(4 + i bit)))
      val outVecU = out (Vec.fill(4)(UInt(4 bit)))
      val inUIntA = in UInt (5 bit)
      val outUIntA = out UInt (5 bit)
      val outUIntS = out UInt (5 bit)


      val inEnum = in(MyEnum)
      val inEnum2 = in(MyEnum2)
      val inEnumBits = in Bits (1 bit)

      val outEnum = out(MyEnum)
      val outEnum2 = out(MyEnum)
      val outEnum22 = out(MyEnum2)
      val outEnumFromBits = out(MyEnum)
      val outEnumBits = out.Bits()
      val outEnumBool = out.Bool()
      val outEnumBool2 = out.Bool()


      val inBundleToBits = in(new BundleAA)
      val outBundleFromBits = out(new BundleAA)

      val slaveHandshakeUInt = slave Handshake (UInt(3 bit))
      val masterHandshakeSInt = master Handshake (SInt(3 bit))

      val slaveHandshake = slave Handshake (new BundleAA)
      val masterHandshake = master Handshake (new BundleAA)
      val masterHandshakeThrow = master Handshake (new BundleAA)
      val masterHandshakeUInt = master Handshake (UInt(4 bit))

      val inType =  in SInt(4 bit)
      val outType =  out UInt(4 bit)
      //  val outBool = out.Bool()


      /* val inShiftVec = in UInt (4 bit)
       val inShiftHi= in UInt (6 bit)
       val inShiftLow = in UInt (6 bit)
       val outShift = out.Bits ()*/

    }

    io.outEnum22 := io.inEnum2
  /*def doIt( a : Data,b : Data): Unit ={
    a := b
  }
    doIt(io.outType ,io.inType)*/
    io.outType :=  io.inType.toUInt

   /* def doIt[T <: Data,T2 <: T](into : T,from : T2): Unit = {
      into := from
    }*/
   // io := io.inBundle0
/*  val s = new mutable.ArrayBuffer[Vec[Data]]
    s += Vec.fill(4)(UInt())
    s += Vec.fill(4)(Bool())*/
    val a =  Vec.fill(4)(UInt())
    val b = Vec.fill(4)(Bool())
   // a := b
  //  doIt(io.outBundleAA , io.inBundle0) // inBundle0
    io.outBundleAA := io.inBundle0
    io.outBundleA := io.inBundle1
    // io.masterHandshake :== io.slaveHandshake
    io.outVecU := io.inVecU

    io.masterHandshakeSInt << (io.slaveHandshakeUInt ~ io.slaveHandshakeUInt.toBits.toSInt)

    io.masterHandshake << io.slaveHandshake
    io.masterHandshakeThrow << io.slaveHandshake.throwIf(io.cond1)
    io.masterHandshakeUInt << (io.slaveHandshake ~ UInt(3))
    //io.masterHandshakeUInt.bits := UInt(2)
    //var myInt = WeekDay.c
    // myInt := WeekDay.Mon
    val bitsFromBundle = io.inBundleToBits.toBits
    println(bitsFromBundle.getWidth)
    io.outBundleFromBits.fromBits(bitsFromBundle)

    io.outEnumFromBits.fromBits(io.inEnumBits)

    io.outEnum := io.inEnum
    when(MyEnum.s2 == io.inEnum) {
      io.outEnum2 := MyEnum.s1
      io.outEnumBits := MyEnum.s1.toBits
    } otherwise {
      io.outEnum2 := io.inEnum
      io.outEnumBits := io.inEnum.toBits
    }


    io.outEnumBool := io.inEnum == MyEnum.s2
    io.outEnumBool2 := io.inEnum == io.inEnum


    val componentS = Component(new ComponentS)
    io.outUIntS := S.two + componentS.io.outUIntS
    //io.outShift := io.inShiftVec(io.inShiftHi,io.inShiftLow)
    //  io.outVecU := io.inVecU

    // io.outBool := io.a > 2


    val recursiveComponent = Component(new RecursiveComponent(3))
    recursiveComponent.io.input := io.cond0
    io.outBool2 := recursiveComponent.io.output


    println(io.inBundle0.getBitsWidth)
    println(io.inBundle1.getBitsWidth)
    println(io.inVecU.getBitsWidth)


    val reg = (UInt())
    reg := io.inu4b + io.inu4b
    io.outu := reg


    val bundleClone = io.inBundle0.clone().keep
    bundleClone := io.inBundle0

    val vecClone = io.inVecU.clone().keep
    vecClone := io.inVecU


    val keepMePlease = io.inu4b + io.inu4b
    keepMePlease.keep


    val blackBoxA = Component(new MyBlackBox)
    val blackBoxB = Component(new MyBlackBox)

    blackBoxB.generic.genC := Number(22)
    blackBoxB.generic.genA := UInt(7)
    blackBoxB.generic.genD := SString("Miaou")

    blackBoxA.io.inUIntA := io.inUIntA
    blackBoxB.io.inUIntA := blackBoxA.io.outUIntA
    io.outUIntA := blackBoxB.io.outUIntA + blackBoxB.io.outUIntA + blackBoxB.io.outUIntA


    val componentAA = Component(new ComponentAA)
    componentAA.io.in := io.cond0 && io.cond0
    io.outBool := componentAA.io.out && io.inUIntA(4)
    /*when(io.cond0){
      componentAA.io.in := io.cond0
    }otherwise{
      componentAA.io.in := RegNext(componentAA.io.in)
    }
    io.outBool := componentAA.io.out && io.inUIntA(4)*/
    /* io.outBool := componentAA.io.out


         val temp = Reg(io.inBundle0,io.inBundle3)
         when(!io.cond0) {
           temp := io.inBundle1
           when(io.cond1) {
             temp := io.inBundle2
           }
         }

         io.outBundle := temp*/

    //  io.outBool := Bool(true)

    nameElements()

    /*
        val temp = Reg(new VecA)

        temp := io.inVec
        io.outVec := temp
        temp(io.a) := io.inBundle3
        //temp(io.b).c := UInt(3)(1)

        io.outBundle := io.inVec(io.b)*/

    /*io.outVec := io.inVec
    io.out := io.inVec(io.a)
    io.out2 := io.inVec(io.a)
    io.outVec(1) := io.a*/

    /*   val bundleReg = Reg(io.outBundle)
       when(io.cond0) {
         bundleReg := Mux(io.cond0,io.inBundle0,io.inBundle1)
       } otherwise {
         bundleReg := io.inBundle2
       }
       io.outBundle := bundleReg*/


    // io.outBundle := Mux(io.cond0,io.inBundle0,io.inBundle1)

    //io.outBits := (io.a >> io.b).resize(8).toBits
    //io.outBits := UInt(2,8).toBits
    /*  val uintTemp = new UInt()
      uintTemp := UInt(3)
      when(io.a < io.b) {

        when(io.a(1) && io.b(io.c)) {
          uintTemp := UInt(2)
        } otherwise {

        }
      } otherwise {
        uintTemp := UInt(4)
      }
      io.outBits :=  uintTemp.toBits*/
    // io.outBits := io.a(io.c,io.b)

    /*
        io.outBool := io.a < io.b


        val out = Reg(io.out)
        val out2 = Reg(io.out2)
        out.inputs(0).inputs(4) = io.d + io.d
        //out2.inputs(0).inputs(4) = io.d - io.d

        //  out := io.a + io.b + io.out
        when(io.a < io.b) {
          when(io.a(1) && io.b(io.c)) {
            when(io.cond4) {
              out := io.inBundleA ## io.cond1 ## io.cond0;
            } otherwise {
              out := io.a + io.b
              out2 := io.c
            }
          }
        }

        io.out := out
        io.out2 := out2*/

    /*
        io.outSInt := io.out.toSInt
        val out = Reg(io.out)
        when(io.cond3) {
          when(io.cond4) {
            out := io.inBundleA ## io.cond2 ## io.cond1 ## io.cond0;
          } otherwise {
            out := io.a.resize(5)
          }
        }
        io.out := out

        io.outCat := io.inBundleA.toBits*/


    /*
        when(io.a > io.b) {
          io.out := UInt(3,5)
        }otherwise {
          io.out := io.d
        }
        io.outSInt := io.out.toSInt*/

    /*AA.io.in := io.in
    AB.io.in := io.in
    when(AA.io.out) {
      io.out := io.a
    } otherwise {
      io.out := io.b
    }

    io.outSInt := io.out.toSInt*/


    /*
        val pulledAAA = AA.AAA.temp.pull

        val r0 = Reg(io.default);

        when(io.cond0) {
          when(io.cond1 && pulledAAA) {
            when(io.cond2) {
              r0 := io.a;
            }
            when(io.cond3) {
              r0 := io.b;
            }
          }
        }.otherwise {
          when(io.cond4) {
            r0 := io.c;
          }
        }
        io.out := r0*/

    /* when(io.cond0) {
       when(io.cond1) {
         when(io.cond2) {
           io.out := io.a;
         }
         when(io.cond3) {
           io.out := io.b;
         }
       }
     }.otherwise {
       when(io.cond4) {
         io.out := io.c;
       }
     }*/


    //io.out := Mux(io.sel,io.a,io.b)


    // ClockDomain.pop(clk)

  }


  val bo = out Bool()


  def main(args: Array[String]) {
    println("START")
    var comp: ComponentA = null
   // val h1 = new Handshake(new BundleA())
   // val h2 = h1.clone()

    SpinalMain({
      comp = new ComponentA
      Component(comp)
    })

    new VhdlTestBenchBackend().elaborate(comp)
    println("DONE")



  }

  /* class Data{
     def :=[T <: Data](that : T) = println("data := data")
   }

   class Bits extends Data{

   }

   val b1,b2 = new Bits
   b1 := b2
   class Flow[T <: Data](val gen : T){
     def <<(that : Flow[T]) = {
       this.gen := that.gen
     }
   }

   val f1,f2 = new Flow(new Bits)

   f1 << f2*/

}

