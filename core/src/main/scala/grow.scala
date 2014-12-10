package pouli

import scala.Int
import scalaz.concurrent.Task

/**
  * Typeclass for deciding what to do when the pool is starved
  */
trait GrowStrategy {
  def apply[A](pool: Pool[A]): Task[A]
}

object GrowStrategy {
  /**
    * boundless object pool. anytime someone wants to borrow an object
    * we will create one for them if one isn't available.
    */
  val infinite: GrowStrategy = new GrowStrategy {
    def apply[A](pool: Pool[A]): Task[A] = pool.create_
  }

  /**
    * object pool which is bounded to a max number of instances. if a
    * request comes in and we are already at max, they get enqueued
    */
  def maxSize(max: Int): GrowStrategy = new GrowStrategy {
    def apply[A](pool: Pool[A]): Task[A] =
      if(pool.total < max) pool.create_ else pool.enqueue_
  }

}
