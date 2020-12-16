package com.generic.bank.fraud.client

import cats.syntax.either._
import com.generic.bank.domain.FinancialMessage.Amount.{ Currency, Value }
import com.generic.bank.domain.{ Bic, FinancialMessage }
import com.generic.bank.domain.FinancialMessage.{ Amount, ReceiverBic, SenderBic }
import com.generic.bank.fraud.api
import org.scalatest.funspec.AsyncFunSpec
import org.scalatest.matchers.should

import scala.concurrent.{ ExecutionContext, Future }

class DefaultFraudClientSpec extends AsyncFunSpec with should.Matchers {

  val finMsg = FinancialMessage(
    SenderBic(Bic("senderBic")),
    ReceiverBic(Bic("receiverBic")),
    Amount(Value(110d), Currency.SGD)
  )

  describe("Fraud API domain error translation") {

    it("translates Illegal error of Fraud API into Illegal error of the client domain") {
      val IllegalText = "It is illegal to pay with pig tails nowadays!"
      val illegalCall: FinancialMessage => Future[Either[api.Error.Illegal, api.domain.FraudResult]] =
        _ => {

          Future.successful(api.Error.Illegal(IllegalText).asLeft)
        }

      new DefaultFraudClient(illegalCall)(ExecutionContext.global).call(
        finMsg
      ).map {
        case Left(Error.Illegal(`IllegalText`)) => succeed
        case x                                  => fail(s"$x is unexpected in this test")
      }
    }

    it("translates System error of Fraud API into Downstream error of the client domain") {

      val failedCall: FinancialMessage => Future[Either[api.Error.System, api.domain.FraudResult]] =
        _ => {
          Future.successful(api.Error.System(new RuntimeException("The API is down")).asLeft)
        }

      new DefaultFraudClient(failedCall)(ExecutionContext.global).call(
        finMsg
      ).map {
        case Left(Error.DownstreamFailure(_)) => succeed
        case x                                => fail(s"$x is unexpected in this test")
      }
    }

    it("handles client side error of Fraud Client as ClientFailure error") {

      val failedCall: FinancialMessage => Future[Either[api.Error.System, api.domain.FraudResult]] =
        _ => {
          Future.failed(new RuntimeException("The FraudClient has failed"))
        }

      new DefaultFraudClient(failedCall)(ExecutionContext.global).call(
        finMsg
      ).map {
        case Left(Error.ClientFailure(_)) => succeed
        case x                            => fail(s"$x is unexpected in this test")
      }
    }
  }

  describe("Fraud API domain FraudResult translation") {

    it("adopts Fraud API FraudResult.Fraud into the client domain ") {
      val successfulCall: FinancialMessage => Future[Either[api.Error, api.domain.FraudResult]] =
        _ => Future.successful(api.domain.FraudResult.Fraud.asRight)

      new DefaultFraudClient(successfulCall)(ExecutionContext.global).call(
        finMsg
      ).map {
        case Right(domain.FraudResult.Fraud) => succeed
        case x                               => fail(s"$x is unexpected in this test")
      }

    }

    it("adopts Fraud API FraudResult.NoFraud into the client domain ") {
      val successfulCall: FinancialMessage => Future[Either[api.Error, api.domain.FraudResult]] =
        _ => Future.successful(api.domain.FraudResult.NoFraud.asRight)

      new DefaultFraudClient(successfulCall)(ExecutionContext.global).call(
        finMsg
      ).map {
        case Right(domain.FraudResult.NoFraud) => succeed
        case x                                 => fail(s"$x is unexpected in this test")
      }

    }
  }

}
