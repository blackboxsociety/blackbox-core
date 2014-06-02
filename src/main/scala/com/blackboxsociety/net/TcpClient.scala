package com.blackboxsociety.net

import scalaz.{Reader => _, _}
import scalaz.syntax.bind._
import com.blackboxsociety._
import scalaz.concurrent._
import scalaz.concurrent.Task._
import scalaz.stream.{async => streamAsync, _}
import java.nio.channels._
import java.nio._
import java.io.IOException
import scodec.bits.ByteVector
import com.blackboxsociety.util._

trait TcpClient {
  def reader(): Process[Task, ByteVector]
  def stringReader(): Process[Task, String]
  def write(b: Array[Byte]): Task[Unit]
  def write(s: String): Task[Unit]
  def write(b: ByteBuffer): Task[Unit]
  def write(s: ByteVector): Task[Unit]
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
      def read(): Task[Finishable[ByteVector]] = async { next =>
        EventLoop.addSocketRead(s, { () =>
          val buffer = ByteBuffer.allocate(32768)
          try {
            s.read(buffer) match {
              case -1 => next(\/-(Done(ByteVector.view(buffer))))
              case _  => next(\/-(More(ByteVector.view(buffer))))
            }
          } catch {
            case e: IOException => next(-\/(e))
          }
        })
      }
      def go(): Process[Task, ByteVector] =
        Process.await[Task, Finishable[ByteVector], ByteVector](read()) {
          case Done(b) => Process.emit(b)
          case More(b) => Process.Emit(Seq(b), go())
        }
      go()
    }

    def stringReader(): Process[Task, String] = reader().pipe(text.utf8Decode)

    def writer(): Sink[Task, ByteVector] = {
      def go(): Sink[Task, ByteVector] =
        Process.await[Task, ByteVector => Task[Unit], ByteVector => Task[Unit]](Task.now(write _)) { f =>
          Process.Emit(Seq(f), go())
        }
      go()
    }

    def write(b: Array[Byte]): Task[Unit] = async { next =>
      val buffer = ByteBuffer.allocate(b.length)
      buffer.clear()
      buffer.put(b)
      buffer.flip()
      write(buffer).runAsync(next)
    }

    def write(b: ByteVector): Task[Unit] = write(b.toByteBuffer)

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
