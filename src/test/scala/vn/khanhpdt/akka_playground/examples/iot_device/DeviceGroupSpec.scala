package vn.khanhpdt.akka_playground.examples.iot_device

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import org.scalatest.WordSpecLike

class DeviceGroupSpec extends ScalaTestWithActorTestKit with WordSpecLike {

  import Device._
  import DeviceGroup._

  "Device group actor" must {

    "allow to register new device" in {
      val deviceGroup = spawn(DeviceGroup("group1"))
      val probe = createTestProbe[DeviceRegistered]()

      deviceGroup ! RegisterDevice(42, "group1", "device1", probe.ref)

      val deviceActor = probe.receiveMessage().deviceRef

      // check that the new device actor is working
      val recordProbe = createTestProbe[TemperatureRecorded]()
      deviceActor ! RecordTemperature(43, 1.2, recordProbe.ref)
      recordProbe.expectMessage(TemperatureRecorded(43))

      val readProbe = createTestProbe[RespondTemperature]()
      deviceActor ! ReadTemperature(44, readProbe.ref)
      readProbe.expectMessage(RespondTemperature(44, Some(1.2)))
    }

    "return the existing device actor" in {
      val deviceGroup = spawn(DeviceGroup("group1"))
      val probe = createTestProbe[DeviceRegistered]()

      deviceGroup ! RegisterDevice(42, "group1", "device1", probe.ref)
      val response1 = probe.receiveMessage()

      deviceGroup ! RegisterDevice(43, "group1", "device1", probe.ref)
      val response2 = probe.receiveMessage()

      response1.deviceRef shouldBe response2.deviceRef
    }

  }
}
