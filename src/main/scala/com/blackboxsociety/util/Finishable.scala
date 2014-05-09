package com.blackboxsociety.util

import scalaz.Functor

sealed trait Finishable[A] {
  val value: A
}

case class More[A](value: A) extends Finishable[A]
case class Done[A](value: A) extends Finishable[A]

object Finishable {

  implicit val FinishableFunctor = new Functor[Finishable] {
    override def map[A, B](fa: Finishable[A])(f: (A) => B): Finishable[B] = fa match {
      case More(m) => More(f(m))
      case Done(d) => Done(f(d))
    }
  }

  def more[A](a: A): Finishable[A] = More(a)

  def done[A](a: A): Finishable[A] = Done(a)

}