package vn.khanhpdt.akka_playground.examples.iot_device

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import org.scalatest.WordSpecLike

class DeviceGroupSpec extends ScalaTestWithActorTestKit with WordSpecLike {

  import Device._
  import DeviceManager._

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
      readProbe.expectMessage(RespondTemperature(44, "device1", Some(1.2)))
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

    "list all current registered devices" in {
      val deviceGroup = spawn(DeviceGroup("group1"))
      val probe = createTestProbe[DeviceRegistered]()

      deviceGroup ! RegisterDevice(1, "group1", "device1", probe.ref)
      deviceGroup ! RegisterDevice(2, "group1", "device2", probe.ref)
      deviceGroup ! RegisterDevice(3, "group1", "device3", probe.ref)

      val readProbe = createTestProbe[DeviceList]()
      deviceGroup ! ListAllDevices(4, "group1", readProbe.ref)

      readProbe.expectMessage(DeviceList(4, "group1", Set("device1", "device2", "device3")))
    }

    "update device list when a device actor is stopped" in {
      val deviceGroup = spawn(DeviceGroup("group1"))
      val probe = createTestProbe[DeviceRegistered]()
      val readProbe = createTestProbe[DeviceList]()

      deviceGroup ! RegisterDevice(1, "group1", "device1", probe.ref)
      val deviceToStop = probe.receiveMessage().deviceRef
      deviceGroup ! RegisterDevice(2, "group1", "device2", probe.ref)
      deviceGroup ! RegisterDevice(3, "group1", "device3", probe.ref)

      deviceGroup ! ListAllDevices(4, "group1", readProbe.ref)
      readProbe.expectMessage(DeviceList(4, "group1", Set("device1", "device2", "device3")))

      deviceToStop ! Stop
      probe.expectTerminated(deviceToStop, probe.remainingOrDefault)

      readProbe.awaitAssert {
        deviceGroup ! ListAllDevices(5, "group1", readProbe.ref)
        readProbe.expectMessage(DeviceList(5, "group1", Set("device2", "device3")))
      }
    }

  }
}
