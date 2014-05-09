package com.blackboxsociety

import java.nio.channels._
import java.nio.channels.spi._

trait Notifiable {
  def notify(channel: AbstractSelectableChannel): Unit
}

object EventLoop {

  var selector = Selector.open
  var sockets: Map[SelectableChannel, SelectionKey] = Map()
  var queues: Map[SelectionKey, Map[Int, List[() => Unit]]] = Map()

  def addServerSocketAccept(socket: ServerSocketChannel, next: () => Unit): Unit = {
    register(socket, SelectionKey.OP_ACCEPT, next)
  }

  def addSocketRead(socket: SocketChannel, next: () => Unit): Unit = {
    register(socket, SelectionKey.OP_READ, next)
  }

  def addSocketWrite(socket: SocketChannel, next: () => Unit): Unit = {
    register(socket, SelectionKey.OP_WRITE, next)
  }

  def closeChannel(socket: SelectableChannel): Unit = {
    sockets.get(socket) foreach { n =>
      queues = queues - n
      n.cancel()
    }
    sockets = sockets - socket
  }

  def run(): Unit = {
    while(true) {
      selector.select()
      val iter = selector.selectedKeys().iterator()
      while(iter.hasNext) {
        iter.next().attachment().asInstanceOf[() => Unit]()
        iter.remove()
      }
    }
  }

  def genNotifier(key: SelectionKey, op: Int, next: () => Unit): (() => Unit) = () => {
    val queue = for (
      ops   <- queues.get(key);
      queue <- ops.get(op)
    ) yield (ops, queue)

    queue match {
      case None => key.interestOps(0)
      case Some((o, q)) =>
        val next = q.head
        val tail = q.drop(1)
        queues = queues + (key -> (o + (op -> tail)))
        if (tail.isEmpty) {
          key.interestOps(key.interestOps() & (~op))
        }
        next()
    }
  }

  def register(socket: SelectableChannel, op: Int, next: () => Unit): Unit = {
    val key      = keyFromChannel(socket, op, next)
    val ops      = opsFromKey(key)
    val queue    = queueFromOps(ops, op)
    val notifier = genNotifier(key, op, next)
    queues = queues + (key -> (ops + (op -> (queue :+ next))))
    key.interestOps(key.interestOps() | op)
    key.attach(notifier)
  }

  def keyFromChannel(c: SelectableChannel, op: Int, next: () => Unit): SelectionKey =
    sockets.get(c) getOrElse {
      val key = c.register(selector, op)
      sockets = sockets + (c -> key)
      key
    }

  def opsFromKey(k: SelectionKey): Map[Int, List[() => Unit]] =
    queues.getOrElse[Map[Int, List[() => Unit]]](k, Map())

  def queueFromOps(ops: Map[Int, List[() => Unit]], op: Int): List[() => Unit] =
    ops.getOrElse(op, List())

}
