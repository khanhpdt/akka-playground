package vn.khanhpdt.akka_playground.examples.iot_device

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import org.scalatest.WordSpecLike
import scala.concurrent.duration._

class DeviceGroupTemperatureQuerySpec extends ScalaTestWithActorTestKit with WordSpecLike {

  import DeviceGroupTemperatureQuery._

  "DeviceGroupTemperatureQuery actor" must {

    "be able to return temperatures of all devices with temperatures" in {
      val deviceProbes = (1 to 3).map(i => s"device-$i" -> createTestProbe[Device.Command]()).toMap
      val devices = deviceProbes.map { case (device, probe) => device -> probe.ref }
      val readProbe = createTestProbe[DeviceManager.GetDeviceGroupTemperatureResult]()

      val queryActor = spawn(DeviceGroupTemperatureQuery(devices, 1.minutes, readProbe.ref))
      queryActor ! Query(1)

      deviceProbes.values.foreach { device =>
        device.expectMessageType[Device.ReadTemperature]
      }

      var temp = 1.1
      val deviceTemperatures = deviceProbes.keySet.map { deviceId =>
        temp += 1
        deviceId -> temp
      }.toMap

      deviceTemperatures.foreach {
        case (deviceId, temperature) =>
          queryActor ! WrappedRespondTemperature(Device.RespondTemperature(1, deviceId, Some(temperature)))
      }

      readProbe.expectMessage(
        DeviceManager.GetDeviceGroupTemperatureResult(
          1,
          deviceTemperatures.map { case (deviceId, temp) => deviceId -> DeviceManager.DeviceTemperatureAvailable(temp) }
        )
      )
    }

    "be able to return temperatures of all devices with and without temperature" in {
      val deviceProbes = (1 to 5).map(i => s"device-$i" -> createTestProbe[Device.Command]()).toMap
      val devices = deviceProbes.map { case (device, probe) => device -> probe.ref }
      val (devicesWithTemperature, devicesWithoutTemperature) = deviceProbes.splitAt(3)
      val readProbe = createTestProbe[DeviceManager.GetDeviceGroupTemperatureResult]()

      val queryActor = spawn(DeviceGroupTemperatureQuery(devices, 1.minutes, readProbe.ref))
      queryActor ! Query(1)

      deviceProbes.values.foreach { device =>
        device.expectMessageType[Device.ReadTemperature]
      }

      var temp = 1.1
      val deviceTemperatures = devicesWithTemperature.keySet.map { deviceId =>
        temp += 1
        deviceId -> temp
      }.toMap
      deviceTemperatures.foreach {
        case (deviceId, temperature) =>
          queryActor ! WrappedRespondTemperature(Device.RespondTemperature(1, deviceId, Some(temperature)))
      }

      devicesWithoutTemperature.foreach {
        case (deviceId, _) =>
          queryActor ! WrappedRespondTemperature(Device.RespondTemperature(1, deviceId, None))
      }

      val expectedTemperatures =
        deviceTemperatures.map { case (deviceId, temp) => deviceId -> DeviceManager.DeviceTemperatureAvailable(temp) } ++
          devicesWithoutTemperature.map { case (deviceId, _) => deviceId -> DeviceManager.DeviceTemperatureNotAvailable }

      readProbe.expectMessage(DeviceManager.GetDeviceGroupTemperatureResult(1, expectedTemperatures))
    }

    "be able to handle stopped devices" in {
      val deviceProbes = (1 to 5).map(i => s"device-$i" -> createTestProbe[Device.Command]()).toMap
      val devices = deviceProbes.map { case (device, probe) => device -> probe.ref }
      val (devicesWithTemperature, devicesToStop) = deviceProbes.splitAt(3)
      val readProbe = createTestProbe[DeviceManager.GetDeviceGroupTemperatureResult]()

      val queryActor = spawn(DeviceGroupTemperatureQuery(devices, 1.minutes, readProbe.ref))
      queryActor ! Query(1)

      deviceProbes.values.foreach { device =>
        device.expectMessageType[Device.ReadTemperature]
      }

      var temp = 1.1
      val deviceTemperatures = devicesWithTemperature.keySet.map { deviceId =>
        temp += 1
        deviceId -> temp
      }.toMap
      deviceTemperatures.foreach {
        case (deviceId, temperature) =>
          queryActor ! WrappedRespondTemperature(Device.RespondTemperature(1, deviceId, Some(temperature)))
      }

      devicesToStop.foreach {
        case (_, deviceProbe) =>
          deviceProbe.stop()
      }

      val expectedTemperatures =
        deviceTemperatures.map { case (deviceId, temp) => deviceId -> DeviceManager.DeviceTemperatureAvailable(temp) } ++
          devicesToStop.map { case (deviceId, _) => deviceId -> DeviceManager.DeviceNotAvailable }

      readProbe.expectMessage(DeviceManager.GetDeviceGroupTemperatureResult(1, expectedTemperatures))
    }

    "be able to respond latest temperatures of stopped devices" in {
      val deviceProbes = (1 to 5).map(i => s"device-$i" -> createTestProbe[Device.Command]()).toMap
      val devices = deviceProbes.map { case (device, probe) => device -> probe.ref }
      val (devicesWithTemperature, devicesToStop) = deviceProbes.splitAt(3)
      val readProbe = createTestProbe[DeviceManager.GetDeviceGroupTemperatureResult]()

      val queryActor = spawn(DeviceGroupTemperatureQuery(devices, 1.minutes, readProbe.ref))
      queryActor ! Query(1)

      deviceProbes.values.foreach { device =>
        device.expectMessageType[Device.ReadTemperature]
      }

      var temp = 1.1
      val deviceTemperatures = devicesWithTemperature.keySet.map { deviceId =>
        temp += 1
        deviceId -> temp
      }.toMap
      deviceTemperatures.foreach {
        case (deviceId, temperature) =>
          queryActor ! WrappedRespondTemperature(Device.RespondTemperature(1, deviceId, Some(temperature)))
      }

      // before being stopped, the device devicesToStop.head send out a temperature and we expect that we should receive
      // this temperature
      queryActor ! WrappedRespondTemperature(Device.RespondTemperature(1, devicesToStop.head._1, Some(12.3)))

      devicesToStop.foreach {
        case (_, deviceProbe) =>
          deviceProbe.stop()
      }

      val expectedTemperatures =
        deviceTemperatures.map { case (deviceId, temp) => deviceId -> DeviceManager.DeviceTemperatureAvailable(temp) } ++
          Map(devicesToStop.head._1 -> DeviceManager.DeviceTemperatureAvailable(12.3)) ++
          devicesToStop.tail.map { case (deviceId, _) => deviceId -> DeviceManager.DeviceNotAvailable }

      readProbe.expectMessage(DeviceManager.GetDeviceGroupTemperatureResult(1, expectedTemperatures))
    }

    "be able to respond temperatures of timed-out devices" in {
      val deviceProbes = (1 to 5).map(i => s"device-$i" -> createTestProbe[Device.Command]()).toMap
      val devices = deviceProbes.map { case (device, probe) => device -> probe.ref }
      val (devicesWithTemperature, devicesTimedOut) = deviceProbes.splitAt(3)
      val readProbe = createTestProbe[DeviceManager.GetDeviceGroupTemperatureResult]()

      val queryActor = spawn(DeviceGroupTemperatureQuery(devices, 300.milliseconds, readProbe.ref))
      queryActor ! Query(1)

      deviceProbes.values.foreach { device =>
        device.expectMessageType[Device.ReadTemperature]
      }

      var temp = 1.1
      val deviceTemperatures = devicesWithTemperature.keySet.map { deviceId =>
        temp += 1
        deviceId -> temp
      }.toMap
      deviceTemperatures.foreach {
        case (deviceId, temperature) =>
          queryActor ! WrappedRespondTemperature(Device.RespondTemperature(1, deviceId, Some(temperature)))
      }

      // to simulate timeout, we just simply not send out any message from that device

      val expectedTemperatures =
        deviceTemperatures.map { case (deviceId, temp) => deviceId -> DeviceManager.DeviceTemperatureAvailable(temp) } ++
          devicesTimedOut.map { case (deviceId, _) => deviceId -> DeviceManager.DeviceQueryTimedOut }

      readProbe.expectMessage(DeviceManager.GetDeviceGroupTemperatureResult(1, expectedTemperatures))
    }

  }
}
