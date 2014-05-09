package com.blackboxsociety.util

sealed trait Finishable[A]
case class More[A](value: A) extends Finishable[A]
case class Done[A](value: A) extends Finishable[A]