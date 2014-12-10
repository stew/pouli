package pouli
package benchmark

import java.util.concurrent.Callable
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.Random
import java.lang.{Runtime,String,System,Thread}
import java.util.concurrent.atomic.AtomicLong
import scala.{Array,Long,List,Nil,Range,Unit}
import scalaz.syntax.std.string._

case class Foo(l: Long)

import scalaz.{Coyoneda,Free,~>}
import scalaz.concurrent.Task


abstract class Benchmark[P[_], O] {
  type Pool = P[O]
  trait B[A] {
    def lift = Free.liftFC(this)
  }

  val borrows = new AtomicInteger(0)
  val rnd = new Random
  var die = new AtomicBoolean(false)

  case object CreatePool extends B[Pool]
  case class Borrow(pool: Pool) extends B[O]
  case class Return(pool: Pool, obj: O) extends B[Unit]
  case class Broken(pool: Pool, obj: O) extends B[Unit]
  case object Delay extends B[Unit]

  type BenchmarkF[A] = Coyoneda[B, A]
  type BenchmarkM[A] = Free.FreeC[B, A]

  def interp: B ~> Task

  def delay: Task[Unit] = Task.delay(Thread.sleep(rnd.nextInt(10)))

  val nextFoo = new AtomicLong(0)
  def createFoo: Foo = Foo(nextFoo.incrementAndGet())

  class Worker(pool: Pool) extends Thread {
    override def run(): Unit = {
      while(!die.get) {
        val doit = for {
          o <- Borrow(pool).lift
//          _ <- Delay.lift
          _ <- if(rnd.nextInt(100) < 5) Broken(pool, o).lift else Return(pool, o).lift
        } yield (())

        Free.runFC(doit)(interp).run
      }
    }
  }

  def usage() {
    System.out.println("must supply a numThreads argument")
    Runtime.getRuntime().exit(1)
  }

  def main(argv: Array[String]): Unit = {

    argv(0).parseInt fold({ _ => usage() },
                          { numThreads =>
    var threads: List[Thread] = Nil
    val sim = CreatePool.lift map { pool =>
      threads = (Range(1,numThreads)).map(_ => new Worker(pool)).toList
    }
    val simTask = Free.runFC(sim)(interp)
    simTask.runAsyncInterruptibly(_ => (),die)
    threads.foreach(_.start)
    Thread.sleep(20000)
    die.set(true)
    threads.foreach(_.join)
                            System.out.println("number of borrows: " + borrows.get)
                          }
    )}
}
