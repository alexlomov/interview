package com.generic.bank

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.{ActorAttributes, Materializer, Supervision}
import akka.stream.scaladsl.{RunnableGraph, Sink, Source}
import cats.syntax.either._
import cats.syntax.option._
import com.generic.bank.stream.incoming.IncomingStream
import com.generic.bank.config.{ApplicationConfig, Config}
import com.generic.bank.domain.FinancialMessage
import com.generic.bank.fraud.api.DefaultFraudApi
import com.generic.bank.fraud.client.DefaultFraudClient
import com.generic.bank.modules.ActorSystemModule
import com.generic.bank.parsing.ValidatingMessageParser
import com.generic.bank.stream.LogError

import java.io.File
import scala.concurrent.{ExecutionContext, ExecutionContextExecutor, Future}
import scala.util.control.NonFatal

object Main extends App {
  implicit val actorSystem: ActorSystem = ActorSystem("financial-messages-stream")
  implicit val executionContext: ExecutionContextExecutor = actorSystem.dispatcher
  implicit val mat: Materializer = Materializer(actorSystem)

  val fraudApi = new DefaultFraudApi(
    ActorSystemModule(actorSystem)
  )
  val fraudClient = new DefaultFraudClient(
    fraudApi.handle
  )(ExecutionContext.global)

  val config: Source[ApplicationConfig, NotUsed] = Source.lazyFuture { () =>
    Future.fromTry {
      Config.get
    }
  }


  val files: ApplicationConfig => Source[Either[stream.incoming.Error, File], NotUsed] = { cfg =>
    new IncomingStream(cfg).source()
  }

  val messages: File => Source[Either[LogError[_], FinancialMessage], NotUsed] = { f =>
    Source.single(
      ValidatingMessageParser.parse(f)
    ).adaptEither
  }

  val isFraudulent: FinancialMessage => Source[Either[fraud.client.Error, FinancialMessage], NotUsed] = fm =>
    Source.lazyFuture { () =>
      fraudClient.call(fm).map { either =>
        either.map { res =>
          fm.copy(
            isFraud = (res == fraud.client.domain.FraudResult.Fraud).some
          )
        }
      }
    }


  val run: RunnableGraph[NotUsed] =
    config.flatMapConcat(files)
    .flatMap(messages)
    .flatMap(isFraudulent)
    .to {
      Sink.foreach {
        case Right(fm @ FinancialMessage(_, _, _, Some(true))) =>
          Console.out.println(s"Got a new SWIFT message, and it was fraudulent!\n$fm")
        case Right(fm @ FinancialMessage(_, _, _, Some(false))) =>
          Console.out.println(s"Got a SWIFT message\n $fm")
        case Right(fm @ FinancialMessage(_, _, _, None)) =>
          Console.err.println(s"Got a SWIFT message that was NOT CHECKED FOR FRAUD!\n $fm")
        case Left(le) =>
          Console.err.println(s"Error processing incoming SWIFT message!\n${le.e}")
      }
    }

  val supervisionResumeNonFatal: Supervision.Decider = {
    case NonFatal(_) => Supervision.Resume
    case _ => Supervision.Stop
  }

  run.withAttributes(
    ActorAttributes.supervisionStrategy(supervisionResumeNonFatal)
  ).run()

  implicit class SourceEitherSyntax[E, A](val source: Source[Either[E, A], NotUsed]) extends AnyVal {
    def adaptEither: Source[Either[LogError[E],A], NotUsed] = source.map(_.leftMap {
      case le: LogError[E] => LogError(le.e)
      case t => LogError(t)
      }
    )

    def flatMap[EE >: E , B](f: A => Source[Either[EE, B], NotUsed]): Source[Either[LogError[_], B], NotUsed] =
      source.flatMapConcat {
        case Left(err) =>
          Source.single(LogError(err).asLeft[B])
        case Right(value) =>
          f(value).adaptEither
      }
  }
}
