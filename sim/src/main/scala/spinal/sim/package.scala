package spinal

import scala.annotation.StaticAnnotation
import scala.collection.mutable.ArrayBuffer

package object sim {
  import scala.collection.IterableLike
  class suspendable extends StaticAnnotation
  implicit class cpsIterable[A, Repr](xs: IterableLike[A, Repr]) {
    def suspendable = new {
      def foreach[B](f: A => Any@suspendable): Unit@suspendable = {
        val it = xs.iterator
        while(it.hasNext) f(it.next)
      }

      def map[R](f: A => R@suspendable): Seq[R]@suspendable ={
        val i = xs.iterator
        val ret = ArrayBuffer[R]()
        while(i.hasNext){
          ret += f(i.next())
        }
        ret
      }
    }
  }


  def repeatSim(times : Long)(body : => Unit@suspendable): Unit@suspendable ={
    var idx = 0l
    while(idx != times){
      idx += 1
      body
    }
  }
}
