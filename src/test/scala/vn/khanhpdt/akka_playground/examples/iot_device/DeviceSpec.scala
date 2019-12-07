package vn.khanhpdt.akka_playground.examples.iot_device

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import org.scalatest.WordSpecLike

class DeviceSpec extends ScalaTestWithActorTestKit with WordSpecLike {

  import Device._

  "Device actor" must {

    "reply with empty reading if no temperature is known" in {
      val probe = createTestProbe[RespondTemperature]()
      val deviceActor = spawn(Device("group", "device"))

      deviceActor ! ReadTemperature(requestId = 42, replyTo = probe.ref)

      val response = probe.receiveMessage()
      response.requestId shouldBe 42
      response.temperature shouldBe None
    }

    "reply with last written temperature" in {
      val readProbe = createTestProbe[RespondTemperature]()
      val writeProbe = createTestProbe[TemperatureRecorded]()
      val deviceActor = spawn(Device("group", "device"))

      deviceActor ! RecordTemperature(requestId = 42, temperature = 1.1, replyTo = writeProbe.ref)
      writeProbe.receiveMessage().requestId shouldBe 42

      deviceActor ! ReadTemperature(requestId = 43, replyTo = readProbe.ref)
      val response = readProbe.receiveMessage()
      response.requestId shouldBe 43
      response.temperature shouldBe Some(1.1)
    }

  }
}
