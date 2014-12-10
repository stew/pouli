package pouli
package benchmark

import org.apache.commons.pool2.BasePooledObjectFactory
import org.apache.commons.pool2.ObjectPool
import org.apache.commons.pool2.impl.DefaultPooledObject
import org.apache.commons.pool2.impl.GenericObjectPool
import scalaz.~>
import scalaz.concurrent.Task
import scalaz.syntax.apply._
import java.lang.System

object Pool2Benchmark extends Benchmark[ObjectPool, Foo] {

  class FooPoolFactory extends BasePooledObjectFactory[Foo] {
    override def create(): Foo = createFoo
    override def wrap(f: Foo) = new DefaultPooledObject(f)
  }

  override def interp = new (B ~> Task) {
    def apply[A](b: B[A]): Task[A] = b match {
      case CreatePool => Task.delay(new GenericObjectPool(new FooPoolFactory()))
      case Borrow(p: Pool) => Task.delay(p.borrowObject()) <* Task.delay(borrows.incrementAndGet())
      case Return(p: Pool, f: Foo) => Task.delay(p.returnObject(f))
      case Broken(p: Pool, f: Foo) => Task.delay(p.invalidateObject(f))
      case Delay => delay
    }
    
  }
}
