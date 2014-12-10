package pouli

import scala.{Boolean,Int,Long,None,Some,volatile,Unit}
import scala.collection.mutable.Queue
import scalaz.concurrent.{Actor, Task}
import scalaz.{\/,-\/,\/-}
import java.lang.{System,Throwable}
import System.out.println

/**
  * A typeclass for things that can be stored in a pool
  */
trait Poolable[A] {
  /**
    * a way to create an A
    */
  def create: Task[A]

  /**
    * this A is no longer needed, perform any cleanup
    */
  def destroy(a: A): Task[Unit] = Task.now(())

  /** 
    * function called before an item is lent
    */
  def activate(a: A): Task[A] = Task.now(a)

  /**
    * function called when a borrowed object is being returned to the pool
    */
  def passivate(a: A): Task[A] = Task.now(a)
}

object Poolable {
  def simple[A](createObject: Task[A]) = new Poolable[A] {
    override val create = createObject
  }
}

private[pouli] case class Pooled[A](lastUsed: Long, a: A)

object Pooled {
  def apply[A](a: A): Pooled[A] = Pooled(System.currentTimeMillis(), a)
}

class Pool[A](grow: GrowStrategy = GrowStrategy.infinite)(
              implicit P: Poolable[A]) {

  def please: Task[A] = Task.async { actor ! Borrow(_) }

  /**
    * Thanks for letting me borrow this, you can return it to the pool
    */
  def thanksFor(s: A): Unit = { actor ! Return(s) }

  /**
    * This thing you let me borrow appears to now be broken, don't
    * return it to the pool
    */
  def thisIsBroken(s: A): Unit = { actor ! Destroy(s) }

  private var borrows: Queue[Callback] = Queue.empty
  private var available: Queue[Pooled[A]] = Queue.empty

  @volatile var total: Int = 0

  type Callback = Throwable \/ A => Unit

  ////////////////////////////////////////////////////////////////////
  // The queue actor

  // we maintain the state of two queues (one of awaiting borrowers,
  // one of available objects) using an actor

  // The handler function for the actor maintaining the queues
  private val handle: BorrowReturn => Unit = {
    case Borrow(b) => handleBorrow(b)
    case Return(r) => handleReturn(r)
    case Destroy(a) => handleDestroy(a)
  }

  private[this] val actor = new Actor[BorrowReturn](handle)

  // The ADT that the actor handles
  private trait BorrowReturn
  // call the callback an object can be borrowed
  private case class Borrow(callback: Callback) extends BorrowReturn

  // This object should be returned to the pool
  private case class Return(a: A) extends BorrowReturn

  // This object should not be returned to the pool
  private case class Destroy(a: A) extends BorrowReturn


  // called from the actor handler
  private[this] def handleReturn(a: A): Unit = {
    P.passivate(a).runAsync {
      case -\/(e) =>
        handleDestroy(a)
      case \/-(a) =>
        borrows match {
          case Queue(callback, bs @ _*) =>
            P.activate(a) runAsync {
              case -\/(t) =>
                handleDestroy(a)
              case \/-(a) =>
                borrows.dequeue
                callback(\/-(a))
            }
          case _ =>
            available = available += Pooled(a)
        }
    }
  }

  // called from the actor handler
  private[this] def handleBorrow(callback: Callback): Unit =
      available match {
        case Queue(head, tail @ _*) =>
          available.dequeue
          P.activate(head.a).runAsync {
            case -\/(t) =>
              handleDestroy(head.a)
              handleBorrow(callback) // this could loop forever
            case x => callback(x)
          }
        case _ =>
          // retry?
          (grow(this) flatMap P.activate).runAsync(callback)
      }


  // called from the actor handler
  private[this] def handleDestroy(a: A): Unit = {
    P.destroy(a).runAsync(_ => ())
    total = total - 1
  }

  // should not be called unless from the handler
  private[pouli] def enqueue_ : Task[A] = Task.async(x=>borrows.enqueue(x))

  // should not be called unless from the handler
  private[pouli] def create_ : Task[A] = {
    P.create onFinish {
      case Some(x) => Task.delay(())// log
      /*!*/case None => Task.delay { total += 1 }
    }
  }
}

