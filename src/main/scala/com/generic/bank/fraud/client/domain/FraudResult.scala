package com.generic.bank.fraud.client.domain

import com.generic.bank.fraud.api

sealed trait FraudResult extends Product with Serializable

object FraudResult {
  case object Fraud extends FraudResult
  case object NoFraud extends FraudResult

  implicit class ApiFraudResultSyntax(val apiFraudResult: api.domain.FraudResult) {
    def adopt: FraudResult = apiFraudResult match {
      case api.domain.FraudResult.Fraud   => Fraud
      case api.domain.FraudResult.NoFraud => NoFraud
    }
  }

}
