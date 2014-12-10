package pouli
package benchmark

import scalaz.~>
import scalaz.concurrent.Task
import scalaz.syntax.apply._
import java.lang.System

object PouliBenchmark extends Benchmark[Pool, Foo] {
  implicit val poolableFoo = Poolable.simple(Task.delay(createFoo))
  override def interp = new (B ~> Task) {
    def apply[A](b: B[A]): Task[A] = b match {
      case CreatePool => Task.delay(new pouli.Pool[Foo]())
      case Borrow(p: Pool) => p.please <* Task.delay(borrows.incrementAndGet())
      case Return(p: Pool, f: Foo) => Task.delay(p.thanksFor(f))
      case Broken(p: Pool, f: Foo) => Task.delay(p.thisIsBroken(f))
      case Delay => delay
    }
  }
}
