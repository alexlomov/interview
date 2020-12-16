package com.generic.bank.stream.incoming

sealed trait Error extends Product with Serializable

object Error {

  case object DirectoryNotFound extends Error
  case class System(underlying: Throwable) extends Error

}
