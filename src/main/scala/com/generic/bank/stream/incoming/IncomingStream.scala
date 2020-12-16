package com.generic.bank.stream.incoming

import java.io.File
import java.nio.file.Paths

import akka.NotUsed
import akka.stream.scaladsl._
import cats.syntax.either._
import cats.syntax.traverse._
import com.generic.bank.config.ApplicationConfig
import com.generic.bank.stream.incoming

import scala.util.Try

class IncomingStream(
  applicationConfig: ApplicationConfig
) {

  def source(): Source[Either[Error, File], NotUsed] =
    Source {
      Try(getClass.getResource(applicationConfig.messageFolder.value))
        .toEither
        .leftMap(incoming.Error.System)
        .flatMap(Option(_).toRight(incoming.Error.DirectoryNotFound))
        .map(_.toURI)
        .map(Paths.get)
        .map(_.toFile)
        .flatMap { directory =>
          Either.cond(
            directory.exists() && directory.isDirectory,
            directory.listFiles().filter(_.isFile).toList,
            incoming.Error.DirectoryNotFound
          )
        }.sequence
    }


}
