package com.generic.bank.stream.incoming

import akka.actor.ActorSystem
import akka.stream.testkit.scaladsl.TestSink
import com.generic.bank.config.ApplicationConfig
import com.generic.bank.modules.ActorSystemModule
import org.scalatest.EitherValues
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class IncomingStreamSpec extends AnyWordSpec with Matchers with EitherValues {

  "IncomingStream#source" should {

    val actorSystemModule = ActorSystemModule()

    implicit val actorSystem: ActorSystem = actorSystemModule.actorSystem

    "return a Source of Either of Files" in {
      val applicationConfig = ApplicationConfig(
        ApplicationConfig.MessageFolder("/messages")
      )
      val incomingStream = new IncomingStream(applicationConfig)

      val result = incomingStream.source()

      val testSource = result.map(_.getOrElse(null)).map(_.getName).runWith(TestSink.probe[String])

      testSource.request(6).expectNextUnorderedN(
        List(
          "mt103_1.json",
          "mt103_2.json",
          "mt103_3.json",
          "mt103_4.json",
          "mt202_1.json",
          "mt202_2.json"
        )
      ).expectComplete()

    }

    "return an Error.DirectoryNotFound" in {
      val applicationConfig = ApplicationConfig(
        ApplicationConfig.MessageFolder("not-a-directory")
      )
      val incomingStream = new IncomingStream(applicationConfig)

      val result = incomingStream.source()

      result.map(_.swap.getOrElse(null)).runWith(TestSink.probe[Error]).requestNext() shouldBe Error.DirectoryNotFound

    }
  }

}
