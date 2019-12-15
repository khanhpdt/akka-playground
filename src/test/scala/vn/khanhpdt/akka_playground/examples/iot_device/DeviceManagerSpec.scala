package vn.khanhpdt.akka_playground.examples.iot_device

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import org.scalatest.WordSpecLike

class DeviceManagerSpec extends ScalaTestWithActorTestKit with WordSpecLike {

  import Device._
  import DeviceManager._

  "Device manager actor" must {

    "allow to register new device" in {
      val deviceManager = spawn(DeviceManager())
      val probe = createTestProbe[DeviceRegistered]()

      deviceManager ! RegisterDevice(42, "group1", "device1", probe.ref)

      val deviceActor = probe.receiveMessage().deviceRef

      // check that the new device actor is working
      val recordProbe = createTestProbe[TemperatureRecorded]()
      deviceActor ! RecordTemperature(43, 1.2, recordProbe.ref)
      recordProbe.expectMessage(TemperatureRecorded(43))

      val readProbe = createTestProbe[RespondTemperature]()
      deviceActor ! ReadTemperature(44, readProbe.ref)
      readProbe.expectMessage(RespondTemperature(44, "device1", Some(1.2)))
    }

    "reuse existing device actor" in {
      val deviceManager = spawn(DeviceManager())
      val probe = createTestProbe[DeviceRegistered]()

      deviceManager ! RegisterDevice(42, "group1", "device1", probe.ref)
      val response1 = probe.receiveMessage()

      deviceManager ! RegisterDevice(43, "group1", "device1", probe.ref)
      val response2 = probe.receiveMessage()

      response1.deviceRef shouldBe response2.deviceRef
    }

    "list all current registered devices" in {
      val deviceManager = spawn(DeviceManager())
      val probe = createTestProbe[DeviceRegistered]()

      deviceManager ! RegisterDevice(1, "group1", "device1", probe.ref)
      deviceManager ! RegisterDevice(2, "group1", "device2", probe.ref)
      deviceManager ! RegisterDevice(3, "group1", "device3", probe.ref)

      val readProbe = createTestProbe[DeviceList]()
      deviceManager ! ListAllDevices(4, "group1", readProbe.ref)

      readProbe.expectMessage(DeviceList(4, "group1", Set("device1", "device2", "device3")))
    }

    "update device list when a device actor is stopped" in {
      val deviceManager = spawn(DeviceManager())
      val probe = createTestProbe[DeviceRegistered]()
      val readProbe = createTestProbe[DeviceList]()

      deviceManager ! RegisterDevice(1, "group1", "device1", probe.ref)
      val deviceToStop = probe.receiveMessage().deviceRef
      deviceManager ! RegisterDevice(2, "group1", "device2", probe.ref)
      deviceManager ! RegisterDevice(3, "group1", "device3", probe.ref)

      deviceManager ! ListAllDevices(4, "group1", readProbe.ref)
      readProbe.expectMessage(DeviceList(4, "group1", Set("device1", "device2", "device3")))

      deviceToStop ! Stop
      probe.expectTerminated(deviceToStop, probe.remainingOrDefault)

      readProbe.awaitAssert {
        deviceManager ! ListAllDevices(5, "group1", readProbe.ref)
        readProbe.expectMessage(DeviceList(5, "group1", Set("device2", "device3")))
      }
    }

    "update device list when a device group actor is stopped" in {
      val deviceManager = spawn(DeviceManager())
      val probe = createTestProbe[DeviceRegistered]()
      val readProbe = createTestProbe[DeviceList]()

      deviceManager ! RegisterDevice(1, "group1", "device1", probe.ref)
      deviceManager ! RegisterDevice(2, "group1", "device2", probe.ref)

      deviceManager ! ListAllDevices(4, "group1", readProbe.ref)
      readProbe.expectMessage(DeviceList(4, "group1", Set("device1", "device2")))

      val getDeviceGroupProbe = createTestProbe[GetDeviceGroupResponse]()
      deviceManager ! GetDeviceGroup(5, "group1", getDeviceGroupProbe.ref)
      val deviceGroupRef = getDeviceGroupProbe.receiveMessage().deviceGroup.get
      deviceGroupRef ! DeviceGroup.Stop

      probe.expectTerminated(deviceGroupRef, probe.remainingOrDefault)

      readProbe.awaitAssert {
        deviceManager ! ListAllDevices(6, "group1", readProbe.ref)
        readProbe.expectMessage(DeviceList(6, "group1", Set.empty))
      }
    }

    "be able to query temperatures of all devices in a group" in {
      val deviceManager = spawn(DeviceManager())
      val registerProbe = createTestProbe[DeviceRegistered]()
      val recordProbe = createTestProbe[TemperatureRecorded]()

      val deviceTemperatures = Map("device1" -> 1.1, "device2" -> 1.2, "device3" -> 1.3)
      deviceTemperatures.foreach {
        case (device, temperature) =>
          deviceManager ! RegisterDevice(1, "group1", device, registerProbe.ref)
          registerProbe.receiveMessage().deviceRef ! RecordTemperature(1, temperature, recordProbe.ref)
      }

      val deviceTemperatureNotAvailable = Set("device4", "device5")
      deviceTemperatureNotAvailable.foreach { device =>
        deviceManager ! RegisterDevice(1, "group1", device, registerProbe.ref)
      }

      // register another group to verify this device will not be included in the result
      deviceManager ! RegisterDevice(1, "group2", "device-2-1", registerProbe.ref)

      val temperatureReadProbe = createTestProbe[GetDeviceGroupTemperatureResult]()
      deviceManager ! GetDeviceGroupTemperature(5, "group1", temperatureReadProbe.ref)

      val expectedTemperatures =
        deviceTemperatures.map { case (device, temperature) => device -> DeviceTemperatureAvailable(temperature)} ++
          deviceTemperatureNotAvailable.map(_ -> DeviceTemperatureNotAvailable)
      temperatureReadProbe.expectMessage(GetDeviceGroupTemperatureResult(5, expectedTemperatures))
    }
  }
}
