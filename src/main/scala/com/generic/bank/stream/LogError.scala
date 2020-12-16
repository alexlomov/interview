package com.generic.bank.stream

import scala.util.control.NoStackTrace

final class LogError[E](val e: E) extends NoStackTrace

object LogError {
  def apply[E](e: E) = new LogError[E](e)
}
