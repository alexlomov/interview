package com.generic.bank.parsing

import cats.syntax.either._
import com.generic.bank.domain.{ Bic, FinancialMessage }
import io.circe.{ parser, HCursor, Json }

import java.io.File
import java.nio.file.{ Files, Path }

object ValidatingMessageParser extends MessageParser {

  type ErrorInFile = String => Error

  override def parse(file: File): Either[Error, FinancialMessage] = {
    val finMsgOrError = for {
      raw <- fileToString(file)
      j <- parseJson(raw)
      c = j.hcursor
      mt <- messageType(c)
      fcs <- fieldCodes(mt)
      (amountCode, senderCode, receiverCode) = fcs
      amount <- parseAmount(c, amountCode)
      senderBic <- parseBic(c, senderCode)(FinancialMessage.SenderBic.apply)
      receiverBic <- parseBic(c, receiverCode)(FinancialMessage.ReceiverBic.apply)
      finMsgOrErr = FinancialMessage(senderBic, receiverBic, amount)
    } yield finMsgOrErr
    finMsgOrError.leftMap { errInFile =>
      errInFile(file.getName)
    }
  }

  def fileToString(file: File): Either[ErrorInFile, String] =
    Either.catchNonFatal {
      Files.readString(
        Path.of(file.toURI)
      )
    }.leftMap { e =>
      LoadFailure(_, e.getMessage)
    }

  def parseJson(rawString: String): Either[ErrorInFile, Json] =
    parser.parse(rawString).leftMap { e =>
      ParseFailure(_, e.getMessage)
    }

  def messageType(c: HCursor): Either[ErrorInFile, String] =
    c.downField("messageType").as[String].leftMap { _ =>
      MissingField(_, "messageType")
    }

  def parseAmount(
    cursor: HCursor,
    fieldCode: AmountFieldCode
  ): Either[ErrorInFile, FinancialMessage.Amount] =
    for {
      f <- cursor.downField(fieldCode.value).as[String].leftMap { _ =>
        MissingField(_, fieldCode.value)
      }
      groups = "^([A-Z]{3})(\\d{1,15})$".r.findAllIn(f)
      rawCurr <- Either.catchNonFatal(
        groups.group(1)
      ).leftMap { e =>
        FieldFormatFailure(_, fieldCode.value, s"Failed parsing currency code: ${e.getMessage}")
      }
      curr <- FinancialMessage.Amount.Currency.withNameEither(rawCurr).leftMap { _ =>
        FieldFormatFailure(_, fieldCode.value, s"Unknown currency $rawCurr")
      }
      rawAmount <- Either.catchNonFatal(
        groups.group(2).toDouble
      ).leftMap { e =>
        FieldFormatFailure(_, fieldCode.value, s"Failed parsing financial amount: ${e.getMessage}")
      }
      amount = FinancialMessage.Amount.Value(rawAmount)
    } yield FinancialMessage.Amount(amount, curr)

  def parseBic[A <: BicDependentType](
    c: HCursor,
    fieldCode: A
  )(
    targetBic: Bic => fieldCode.BicOf
  ): Either[ErrorInFile, fieldCode.BicOf] =
    c.downField(fieldCode.value).as[String].leftMap { _ =>
      MissingField(_, fieldCode.value)
    }.map(Bic.apply)
      .map(targetBic)

  def fieldCodes(
    messageType: String
  ): Either[ErrorInFile, (AmountFieldCode, SenderFieldCode, ReceiverFieldCode)] = messageType match {
    case "MT103" => (AmountFieldCode("33B"), SenderFieldCode("51A"), ReceiverFieldCode("57A")).asRight
    case "MT202" => (AmountFieldCode("32A"), SenderFieldCode("52A"), ReceiverFieldCode("58A")).asRight
    case unk     => (UnsupportedMessageType(_, unk)).asLeft
  }

  sealed trait BicDependentType extends Product with Serializable {
    val value: String
    type BicOf
  }

  final case class AmountFieldCode(value: String) extends AnyVal
  final case class SenderFieldCode(value: String) extends BicDependentType {
    override type BicOf = FinancialMessage.SenderBic
  }
  final case class ReceiverFieldCode(value: String) extends BicDependentType {
    override type BicOf = FinancialMessage.ReceiverBic
  }

}
