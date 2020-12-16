package com.generic.bank.fraud.client

import cats.syntax.either._
import com.generic.bank.domain.FinancialMessage
import com.generic.bank.fraud.api
import com.generic.bank.fraud.client.domain.FraudResult
import com.generic.bank.fraud.client.domain.FraudResult._
import com.generic.bank.fraud.client.Error._

import scala.concurrent.{ ExecutionContext, Future }

final class DefaultFraudClient(
  apiCall: FinancialMessage => Future[Either[api.Error, api.domain.FraudResult]]
)(
  executionContext: ExecutionContext
) extends FraudClient {
  implicit private val ec: ExecutionContext = executionContext
  override def call(financialMessage: FinancialMessage): Future[Either[Error, FraudResult]] =
    apiCall(financialMessage).map { either =>
      either.bimap(
        _.adopt,
        _.adopt
      )
    }.recover {
      case t: Throwable =>
        ClientFailure(t).asLeft
    }

}
