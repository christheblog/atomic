package org.cc.concurrent.atomic

import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger

import org.scalatest.{BeforeAndAfterAll, FlatSpec, Matchers}

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}


class AtomicSpecs extends FlatSpec with Matchers with BeforeAndAfterAll {

  import org.cc.concurrent.atomic._

  lazy val context =
    ExecutionContext.fromExecutorService(java.util.concurrent.Executors.newFixedThreadPool(4))

  "atomic.tryTransact { ... }" should "always be successful when one only one thread is involed" in  {
    val v = atomic(1)
    v.tryTransact { _ => 2 } should be (Some(1,2))
  }

  it should "always work when the reference holds the value we expect" in  {
    val v = atomic(1)
    v.tryTransact(1) { _ => 2 } should be (Some(1,2))
  }

  it should "return None when the reference does NOT holds the value we expect" in  {
    val v = atomic(1)
    v.tryTransact(3) { _ => 2 } should be (None)
  }

  "atomic.tryTransact { ... } orFail()" should "throw an AtomicRefTransactionFailed when the reference does NOT holds the value we expect" in  {
    val v = atomic(1)
    an [AtomicRefTransactionFailed] should be thrownBy { v.tryTransact(3) { _ => 2 } orFail }
  }

  "atomic.tryTransactF" should "return a successful Future when no concurrent modification has happened" in  {
    val v = atomic(1)
    implicit val ctx = context
    v.tryTransactF { _ => 2 }.onComplete {
      case Success((1,2)) => // test ok
      case unexpected =>
        fail(s"transaction future didn't hold th expected value Success((1,2)) after completion : $unexpected")
    }
  }

  it should "return a failure when a concurrent modification has happened" in  {
    val v = atomic(1)
    implicit val ctx = context
    val l1 = new CountDownLatch(1)
    val l2 = new CountDownLatch(1)
    val result = v.tryTransactF { _ =>
      l1.countDown
      // concurrent change is happening here
      l2.await
      2
    }

    // Waiting before performing concurrent change
    l1.await
    v.set(3)
    l2.countDown

    // Testing transaction failure
    result.onComplete {
      case Failure(_) => // test successful
      case res@Success(_) =>
        fail(s"Transaction was successful despite a concurrent modification: $res")
    }
  }

  "atomic.retry(n times) { ... }" should "allow a counter to be safely incremented by several threads" in {
    val N = 100000
    val T = 10

    val failureCount = new AtomicInteger(0)
    val counter = atomic(0)
    def newThread = new Thread(new Runnable() {
        override def run() {
          for (_ <- 0 until N) {
            counter.retry(10 times) { value => value + 1 }.getOrElse { failureCount.getAndIncrement() }
          }
        }
    })
    val threads = (0 until T).map { _ => newThread }
    threads.foreach(_.start)
    threads.foreach(_.join)

    counter.get() should not be (0)
    (failureCount.get() <= N * threads.size) should be (true)
    (counter.get() + failureCount.get()) should be (N * threads.size)
  }

  "atomic.transact { ... }" should "always be successful on the first attempt when only one thread is involved" in  {
    val counter = new AtomicInteger(0)
    val v = atomic(1)
    v.transact { _ => counter.getAndIncrement; 2 } should be ((1,2))
    counter.get() should be (1)
  }

  it should "allow a counter to be safely incremented by several threads" in {
    val N = 100000
    val T = 10

    val counter = atomic(0)
    def newThread = new Thread(new Runnable() {
      override def run() {
        for (_ <- 0 until N) {
          counter.transact { value => value + 1 }
        }
      }
    })
    val threads = (0 until T).map { _ => newThread }
    threads.foreach(_.start)
    threads.foreach(_.join)

    counter.get() should be (N * threads.size)
  }


  override protected def afterAll() {
    context.shutdownNow()
  }
}
