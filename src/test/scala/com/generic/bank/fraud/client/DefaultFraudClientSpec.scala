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

  describe("Fraud API domain error translation") {

    val finMsg = FinancialMessage(
      SenderBic(Bic("senderBic")),
      ReceiverBic(Bic("receiverBic")),
      Amount(Value(110d), Currency.SGD)
    )
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

  }

}
