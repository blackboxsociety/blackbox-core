package com.blackboxsociety.net

import scalaz.{Reader => _, _}
import scalaz.syntax.bind._
import com.blackboxsociety._
import scalaz.concurrent._
import scalaz.concurrent.Task._
import scalaz.stream._
import scalaz.stream.{async => streamAsync}
import java.nio.channels._
import java.nio._
import com.blackboxsociety.util._
import java.io.IOException
import scodec.bits.ByteVector

trait TcpClient {
  def reader(): Task[Finishable[ByteBuffer]]
  def write(b: Array[Byte]): Task[Unit]
  def write(s: String): Task[Unit]
  def write(c: FileChannel, o: Long = 0): Task[Unit]
  def end(b: Array[Byte]): Task[Unit]
  def end(s: String): Task[Unit]
  def end(c: FileChannel): Task[Unit]
  def close(): Task[Unit]
  def close(n: Unit): Task[Unit]
}

object TcpClient {

  def apply(socket: SocketChannel): TcpClient = {
    socket.configureBlocking(false)
    TcpClientImpl(socket)
  }

  private case class TcpClientImpl(s: SocketChannel) extends TcpClient {

    def reader(): Process[Task, ByteVector] = {
      val (q, src) = streamAsync.queue[ByteVector]
      def reader(): Unit = {
        EventLoop.addSocketRead(s, { () =>
          val buffer = ByteBuffer.allocate(32768)
          try {
            s.read(buffer) match {
              case -1 => q.enqueue(ByteVector.view(buffer)); q.close
              case _ => q.enqueue(ByteVector.view(buffer)); reader()
            }
          } catch {
            case e: IOException => q.fail(e)
          }
        })
      }
      reader()
      src
    }

    def readAsString(): Process[Task, String] = reader().pipe(text.utf8Decode)

    def writer(): Sink[Task, ByteVector] = {
      val (q, src) = streamAsync.queue[ByteVector]
      src.flatMap({ b => Process.eval(write(b.toByteBuffer)) }).runLog.runAsync({ _ => Unit})
      streamAsync.toSink(q, {_ => close()})
    }

    def write(b: Array[Byte]): Task[Unit] = async { next =>
      val buffer = ByteBuffer.allocate(b.length)
      buffer.clear()
      buffer.put(b)
      buffer.flip()
      write(buffer).runAsync(next)
    }

    def write(b: ByteBuffer): Task[Unit] = async { next =>
      EventLoop.addSocketWrite(s, { () =>
        try {
          s.write(b)
          if (b.hasRemaining) {
            write(b).runAsync(next)
          } else {
            next(\/-(Unit))
          }
        } catch {
          case e: IOException => close().runAsync({ _ => next(-\/(e))})
        }
      })
    }

    def write(s: String): Task[Unit] = {
      write(s.getBytes)
    }

    def write(c: FileChannel, o: Long = 0): Task[Unit] = async { next =>
      EventLoop.addSocketWrite(s, { () =>
        val size = c.size()
        try {
          val sent = c.transferTo(o, size, s)
          val rem  = o + sent
          if (rem < c.size()) {
            write(c, rem).runAsync(next)
          } else {
            next(\/-(Unit))
          }
        } catch {
          case e: IOException => close().runAsync({ _ => next(-\/(e))})
        }
      })
    }

    def end(b: Array[Byte]): Task[Unit] = write(b) >>= close

    def end(s: String): Task[Unit] = write(s) >>= close

    def end(c: FileChannel): Task[Unit] = write(c) >>= close

    def close(): Task[Unit] = async { next =>
      EventLoop.closeChannel(s)
      s.close()
      next(\/-(Unit))
    }

    def close(n: Unit): Task[Unit] = close()

  }

}
