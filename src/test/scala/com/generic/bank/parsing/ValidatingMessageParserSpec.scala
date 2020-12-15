package com.generic.bank.parsing

import io.circe.literal._
import cats.syntax.either._
import org.scalatest.matchers.should
import org.scalatest.funspec.AnyFunSpec
import ValidatingMessageParser._
import com.generic.bank.domain.{Bic, FinancialMessage}

class ValidatingMessageParserSpec extends AnyFunSpec with should.Matchers {

  val Filename = "filename"

  describe( "JSON parsing") {

    it("parses a well-formed JSON string into JSON") {
      val rawJson = """{
                      |    "messageType": "MT103",
                      |    "33B": "EUR123",
                      |    "50A": "BE71096123456769",
                      |    "51A": "TNISROB2",
                      |    "57A": "BTRLRO22",
                      |    "59A": "RO09BCYP0000001234567890"
                      |}""".stripMargin
      ValidatingMessageParser.parseJson(rawJson).isRight shouldBe true
    }

    it("returns ParseError attempting to parse malformed JSON string") {
      val rawBadJson = """{
                      |    "messageType"; "MT103", !!! it's a semicolon instead of a colon here !!!
                      |    "33B": "EUR123",
                      |    "50A": "BE71096123456769",
                      |    "51A": "TNISROB2",
                      |    "57A": "BTRLRO22",
                      |    "59A": "RO09BCYP0000001234567890"
                      |}""".stripMargin

      ValidatingMessageParser.parseJson(rawBadJson).leftMap(_(Filename)) should matchPattern {
        case Left(ParseFailure(`Filename`, _)) =>
      }
    }
  }

  describe("message type handling") {

    it("returns MissingField error if \"messageType\" attribute is missing in a message") {
      val json = json"""{
        "33B": "EUR123",
        "50A": "BE71096123456769",
        "51A": "TNISROB2",
        "57A": "BTRLRO22",
        "59A": "RO09BCYP0000001234567890"
      }"""
      ValidatingMessageParser.messageType(json.hcursor).leftMap(_(Filename)) should matchPattern {
        case Left(MissingField(`Filename`, "messageType")) =>
      }
    }

    it("recognises \"MT103\"") {
      ValidatingMessageParser.fieldCodes("MT103").leftMap(_(Filename)) shouldBe Right(
        (AmountFieldCode("33B"), SenderFieldCode("51A"), ReceiverFieldCode("57A"))
      )
    }

    it("recognises \"MT202\"") {
      ValidatingMessageParser.fieldCodes("MT202").leftMap(_(Filename)) shouldBe Right(
        (AmountFieldCode("32A"), SenderFieldCode("52A"), ReceiverFieldCode("58A"))
      )
    }

    it("returns UnsupportedMessageType for unexpected \"messageType\" field value") {
      val wrongTypeOfSwift = "CBR600F"
      ValidatingMessageParser.fieldCodes(wrongTypeOfSwift).leftMap(_(Filename)) should matchPattern {
        case Left(UnsupportedMessageType(`Filename`, `wrongTypeOfSwift`)) =>
      }
    }
  }

  describe("parsing of amount") {

    it("returns MissingField if the expected amount field is missing") {
      val json =
        json"""
           {
              "messageType": "MT103",
              "6789G": "EUR123",
              "50A": "BE71096123456769",
              "51A": "TNISROB2",
              "57A": "BTRLRO22",
              "59A": "RO09BCYP0000001234567890"
          }
        """
      ValidatingMessageParser.parseAmount(
        json.hcursor,
        AmountFieldCode("33B")
      ).leftMap(_(Filename)) should matchPattern {
        case Left(MissingField(`Filename`, "33B")) =>
      }
    }

    it("parses correct amount") {
      val json =
        json"""
           {
              "messageType": "MT103",
              "33B": "EUR123",
              "50A": "BE71096123456769",
              "51A": "TNISROB2",
              "57A": "BTRLRO22",
              "59A": "RO09BCYP0000001234567890"
          }
        """
      ValidatingMessageParser.parseAmount(
        json.hcursor,
        AmountFieldCode("33B")
      ).leftMap(_(Filename)) shouldBe Right(
          FinancialMessage.Amount(
          FinancialMessage.Amount.Value(123),
          FinancialMessage.Amount.Currency.EUR
        )
      )
    }

    it("returns FieldFormatFailure on unexpected currency code") {
      val json =
        json"""
           {
              "messageType": "MT103",
              "33B": "BEF431",
              "50A": "BE71096123456769",
              "51A": "TNISROB2",
              "57A": "BTRLRO22",
              "59A": "RO09BCYP0000001234567890"
          }
        """

      ValidatingMessageParser.parseAmount(
        json.hcursor,
        AmountFieldCode("33B")
      ).leftMap(_(Filename)) should matchPattern {
        case Left(FieldFormatFailure(`Filename`, "33B", _)) =>
      }
    }

    it("returns FieldFormatFailure on invalid amount") {
      val json =
        json"""
           {
              "messageType": "MT103",
              "33B": "BEFBILLION",
              "50A": "BE71096123456769",
              "51A": "TNISROB2",
              "57A": "BTRLRO22",
              "59A": "RO09BCYP0000001234567890"
          }
        """

      ValidatingMessageParser.parseAmount(
        json.hcursor,
        AmountFieldCode("33B")
      ).leftMap(_(Filename)) should matchPattern {
        case Left(FieldFormatFailure(`Filename`, "33B", _)) =>
      }
    }
  }

  describe("parsing BICs") {
    it("parses a valid sender BIC") {

      val senderBic = """"TNISROB2""""
      val json =
        json"""
           {
              "messageType": "MT103",
              "33B": "EUR431",
              "50A": "BE71096123456769",
              "51A": $senderBic,
              "57A": "BTRLRO22",
              "59A": "RO09BCYP0000001234567890"
          }
        """

      ValidatingMessageParser.parseBic(
        json.hcursor,
        SenderFieldCode("51A")
      )(FinancialMessage.SenderBic.apply).leftMap(_ (Filename)) shouldBe Right(
        FinancialMessage.SenderBic(Bic(senderBic))
      )
    }

    it("parses a valid receiver BIC") {

      val receiverBic = "BTRLRO22"
      val json =
        json"""
           {
              "messageType": "MT103",
              "33B": "EUR431",
              "50A": "BE71096123456769",
              "51A": "TNISROB2",
              "57A": $receiverBic,
              "59A": "RO09BCYP0000001234567890"
          }
        """

      ValidatingMessageParser.parseBic(
        json.hcursor,
        ReceiverFieldCode("57A")
      )(FinancialMessage.ReceiverBic.apply).leftMap(_ (Filename)) shouldBe Right(
        FinancialMessage.ReceiverBic(Bic(receiverBic))
      )
    }

    it("returns MissingField if sender BIC field is missing") {
      val json =
        json"""
           {
              "messageType": "MT103",
              "33B": "BEFBILLION",
              "50A": "BE71096123456769",

              "57A": "BTRLRO22",
              "59A": "RO09BCYP0000001234567890"
          }
        """

      ValidatingMessageParser.parseBic(
        json.hcursor,
        SenderFieldCode("51A")
      )(FinancialMessage.SenderBic.apply).leftMap(_ (Filename)) should matchPattern {
        case Left(MissingField(`Filename`, "51A")) =>
      }
    }

    it("returns MissingField if receiver BIC field is missing") {
      val json =
        json"""
           {
              "messageType": "MT103",
              "33B": "BEFBILLION",
              "50A": "BE71096123456769",
              "51A": "TNISROB2",

              "59A": "RO09BCYP0000001234567890"
          }
        """

      ValidatingMessageParser.parseBic(
        json.hcursor,
        ReceiverFieldCode("57A")
      )(FinancialMessage.ReceiverBic.apply).leftMap(_ (Filename)) should matchPattern {
        case Left(MissingField(`Filename`, "57A")) =>
      }
    }

  }


}
