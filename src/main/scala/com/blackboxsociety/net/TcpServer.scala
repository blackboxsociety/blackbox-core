package com.blackboxsociety.net

import java.net._
import scalaz._
import scalaz.syntax.id._
import scalaz.concurrent._
import scalaz.concurrent.Task._
import java.nio.channels._
import com.blackboxsociety._


trait TcpServer {
  def accept(): Task[TcpClient]
  def close(): Task[Unit]
}

object TcpServer {

  def apply(host: String, port: Int): Task[TcpServer] = now {
    val channel = ServerSocketChannel.open
    val address = new InetSocketAddress(port)
    channel.socket().bind(address)
    channel.configureBlocking(false)
    TcpServerImpl(channel)
  }

  private case class TcpServerImpl(s: ServerSocketChannel) extends TcpServer {

    def accept(): Task[TcpClient] = async { next =>
      EventLoop.addServerSocketAccept(s, { () =>
        val client = s.accept()
        next(TcpClient(client).right)
      })
    }

    def close(): Task[Unit] = async { next =>
      EventLoop.closeChannel(s)
      s.close()
      next(\/-(Unit))
    }

  }

}
