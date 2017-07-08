package org.cc.concurrent

package object atomic {

  import java.util.concurrent.atomic.AtomicReference
  import scala.annotation.tailrec
  import scala.concurrent._


  class AtomicRefTransactionFailed(msg: String, cause: Throwable) extends RuntimeException(msg, cause) {
    def this() = this("Transaction failed", null)
    def this(msg: String) = this(msg,null)
  }

  def atomic[T](t: T) = new AtomicReference[T](t)


  // Enriching AtomicReference
  implicit class RichAtomicRef[T](ref: AtomicReference[T]) {

    final def tryTransact(expected: T)(f: T => T): Option[(T,T)] = {
      val next = f(expected)
      if(ref.compareAndSet(expected, next)) Some((expected, next)) else None
    }

    final def tryTransact(f: T => T): Option[(T,T)] = {
      tryTransact(ref.get())(f)
    }

    @tailrec
    final def retry(count: Int)(f: T => T): Option[(T,T)] = {
      require(count >= 0)
      (tryTransact(f), count) match {
        case (r@Some(_), _) => r
        case (None, c) if c > 0 =>
          if(c % 10 == 0) Thread.`yield` // shall we avoid busy spin loops - or not ?
          retry(c-1)(f)
        case _ => None
      }
    }

    @tailrec
    final def transact(f: T => T): (T,T) = {
      tryTransact(f) match {
        case Some(r) => r
        case None =>
          transact(f)
      }
    }

    // Wrapping result into a Future

    final def tryTransactF(expected: T)(f: T => T)(implicit ec: ExecutionContext): Future[(T,T)] = {
      Future { tryTransact(expected)(f).orFail().get }
    }

    final def tryTransactF(f: T => T)(implicit ec: ExecutionContext): Future[(T,T)] = {
      tryTransactF(ref.get())(f)
    }

    final def retryF(count: Int)(f: T => T)(implicit ec: ExecutionContext): Future[(T,T)] = {
      Future { retry(count)(f) } collect { case Some(res) => res }
    }
  }


  // Just for fun ... to unlock useless syntax : retry(n times) { ... }
  implicit class RichInt(i: Int) {
    def time = i
    def times = i
  }

  implicit class RichOption[T](result: Option[T]) {
    def orFail(): Option[T] = result.orElse { throw new AtomicRefTransactionFailed() }
    def orFailWith(t: => Nothing): Option[T] = result.orElse { t }
  }

}
