package com.blackboxsociety.util.parser

import com.blackboxsociety.net._
import scalaz.syntax.bind._
import scalaz.concurrent._
import com.blackboxsociety.util._
import com.blackboxsociety.util.Finishable
import scala.language.implicitConversions

trait ParserStream {
  val current: Finishable[String]
  def latest: Task[ParserStream]
  def withText(s: Finishable[String]): ParserStream
}

object ParserStream {

  implicit def TcpClientToParserStream(client: TcpClient): TcpParserStream = TcpParserStream(client)

  implicit def StringToParserStream(str: String): StringParserStream = StringParserStream(str)

}

case class TcpParserStream(client: TcpClient, current: Finishable[String]= More("")) extends ParserStream {

  def latest: Task[ParserStream] = {
    client.readAsString() map { l =>
      current match {
        case More(c)     => TcpParserStream(client, l map { s => c + s })
        case c @ Done(_) => TcpParserStream(client, c)
      }
    }
  }

  def withText(s: Finishable[String]): ParserStream =
    TcpParserStream(client, s)

}

case class StringParserStream(str: String) extends ParserStream {

  override val current: Finishable[String] = Done(str)

  override def latest: Task[ParserStream] = Task.now(this)

  override def withText(s: Finishable[String]): ParserStream = StringParserStream(s.value)

}