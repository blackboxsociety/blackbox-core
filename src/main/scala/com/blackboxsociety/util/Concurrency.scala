package com.blackboxsociety.util

import java.util.concurrent._
import scalaz.concurrent._
import scalaz.concurrent.Task._
import scalaz.effect._

object Concurrency {

  val pool: ExecutorService = Executors.newFixedThreadPool(32)

  def forkIO(n: IO[Unit]): IO[Unit] = IO {
    pool.execute(new Runnable {
      def run(): Unit = n.unsafePerformIO()
    })
  }

  def forkIO(n: Task[Unit]): Task[Unit] = now {
    n.runAsync({ _ => Unit })
  }

  def forkForever(n: Task[Unit]): Task[Unit] = now {
    n.runAsync({ _ =>
      forkForever(n)
    })
  }

}
