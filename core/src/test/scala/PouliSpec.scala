package pouli
package test

import scalaz.concurrent.Task
import org.scalatest._
import scalaz.Functor

class Foo {
  var touched = false
  def touch(): Unit = touched = true

  var valid = true
  def ruin(): Unit = valid = false

  var invalidated = false
  def invalidate(): Unit = invalidated = true

  var activated = false
  def activate(): Unit = activated = true

  var passivated = false
  def passivate(): Unit = passivated = true
}

object Foo {
  // returns an instance of poolable that lets us audit what has happened
  def poolable = new Poolable[Foo] {
    @volatile var created: Int = 0
    @volatile var total: Int = 0
    @volatile var allCreated: List[Foo] = Nil

    override val create = Task.delay {
      created += 1
      val r = new Foo
      allCreated = r :: allCreated
      r
    }

    override def destroy(f: Foo) = Task.now {
      f.invalidate()
      total -= 1
    }

    override def activate(f: Foo) = Task.delay {
      f.activate()
      f
    }

    override def passivate(f: Foo) = if(f.valid)
      Task.delay {
        f.passivate()
        f
      } else Task.fail(new Throwable("invalid"))
  }
}

class PouliSpec extends FlatSpec with Matchers {
  import Foo._

  behavior of "connection pool"
  it should "create objects" in {
    val poolable = Foo.poolable
    val pool: Pool[Foo] = new Pool()(poolable)
    val f = pool.please.run
    f.valid should be (true)
    poolable.allCreated should be (f :: Nil)
  }

  // returns funtion that given a pool will try to request n objects
  // from the pool at roughly the same time
  def collidingBorrowers[A](n: Int): Pool[A] => Task[Unit] = { pool =>
    val borrow = Task.fork(pool.please)

    val holdIt: A => Unit = { f =>
      Thread.sleep(100)
      pool.thanksFor(f)
    }

    val tasks: Seq[Task[Unit]] = Seq.fill(n)(borrow map holdIt)
    Functor[Task].void(Task.gatherUnordered(tasks))
  }

  it should "not exceed its max" in {
    val poolable = Foo.poolable
    val borrowers = collidingBorrowers[Foo](10)

    1 to 5 foreach { i =>
      val pool: Pool[Foo] = {
        poolable.created = 0
        new Pool(GrowStrategy.maxSize(i))(poolable)
      }
      borrowers(pool).run
      poolable.created should be <= i
    }
  }

  it should "call destroy on broken objects" in {
    val poolable = Foo.poolable
    val pool: Pool[Foo] = new Pool()(poolable)

    (1 to 10) foreach (_ => pool.please.map(f => pool.thisIsBroken(f)).run)

    poolable.allCreated.size should be (10)

    poolable.allCreated.map(_.invalidated) should contain only true
  }

  it should "call activate when lending" in {
    val poolable = Foo.poolable
    val pool: Pool[Foo] = new Pool()(poolable)
    pool.please.map(f => f.activated).run should be (true)
  }

  it should "call passivate when returning" in {
    val poolable = Foo.poolable
    val pool: Pool[Foo] = new Pool()(poolable)
    val f = pool.please.run
    pool.thanksFor(f)
    Thread.sleep(100)
    f.passivated should be (true)
  }

  it should "not return ruined objects" in {
    val poolable = Foo.poolable
    val pool: Pool[Foo] = new Pool()(poolable)

    (1 to 10) foreach (_ => pool.please.map { f => f.ruin() ; pool.thanksFor(f)}.run)

    poolable.allCreated.size should be (10)
    poolable.allCreated.map(_.valid) should contain only false
    poolable.allCreated.map(_.invalidated) should contain only true
  }

}
