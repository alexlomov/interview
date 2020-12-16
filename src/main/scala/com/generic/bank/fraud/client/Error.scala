package com.generic.bank.fraud.client

import com.generic.bank.fraud.api

sealed trait Error extends Product with Serializable

object Error {
  case class Illegal(description: String) extends Error
  case class DownstreamFailure(underlying: Throwable) extends Error
  case class ClientFailure(underlying: Throwable) extends Error

  implicit class ApiErrorSyntax(val apiError: api.Error) {
    def adopt: Error = apiError match {
      case api.Error.Illegal(description) => Illegal(description)
      case api.Error.System(underlying)   => DownstreamFailure(underlying)
    }
  }

}
