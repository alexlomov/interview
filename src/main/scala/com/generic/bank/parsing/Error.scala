package com.generic.bank.parsing

sealed trait Error extends Product with Serializable

final case class UnsupportedMessageType(
  fileName: String,
  encounteredType: String
) extends Error

final case class ParseFailure(
  fileName: String,
  message: String
) extends Error

final case class MissingField(
  fileName: String,
  fieldName: String
) extends Error

final case class FieldFormatFailure(
  fileName: String,
  fieldName: String,
  message: String
) extends Error

final case class LoadFailure(
  fileName: String,
  message: String
) extends Error
