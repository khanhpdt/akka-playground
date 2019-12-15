package vn.khanhpdt.akka_playground.examples.iot_device

import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior, PostStop, Signal}
import scala.concurrent.duration._
import scala.collection.mutable

class DeviceGroup(groupId: String, ctx: ActorContext[DeviceGroup.Command]) extends AbstractBehavior[DeviceGroup.Command](ctx) {

  import DeviceGroup._
  import DeviceManager.{DeviceList, DeviceRegistered, ListAllDevices, RegisterDevice, GetDeviceGroupTemperature}

  ctx.log.info(s"Device group actor [$groupId] starting...")

  private val devices = new mutable.HashMap[String, ActorRef[Device.Command]]()

  override def onMessage(msg: DeviceGroup.Command): Behavior[DeviceGroup.Command] = {
    msg match {
      case RegisterDevice(requestId, `groupId`, deviceId, replyTo) =>
        val deviceActor = devices.getOrElseUpdate(deviceId, {
          val device = ctx.spawn(Device(groupId, deviceId), s"device-$deviceId")
          ctx.watchWith(device, DeviceStopped(groupId, deviceId))
          device
        })
        replyTo ! DeviceRegistered(requestId, deviceActor)
        this
      case RegisterDevice(_, gId, _, _) =>
        ctx.log.warn(s"Ignore RegisterDevice request for another group [$gId]")
        this
      case ListAllDevices(requestId, `groupId`, replyTo) =>
        replyTo ! DeviceList(requestId, groupId, devices.keySet.toSet)
        this
      case ListAllDevices(_, gId, _) =>
        ctx.log.warn(s"Ignore ListAllDevices request for another group [$gId]")
        this
      case DeviceStopped(`groupId`, deviceId) =>
        devices.remove(deviceId)
        this
      case Stop =>
        Behaviors.stopped
      case GetDeviceGroupTemperature(requestId, `groupId`, replyTo) =>
        val query = ctx.spawn(DeviceGroupTemperatureQuery(devices.toMap, 1.minutes, replyTo), s"group-$groupId-temperature-query")
        query ! DeviceGroupTemperatureQuery.Query(requestId)
        this
      case GetDeviceGroupTemperature(_, gId, _) =>
        ctx.log.warn(s"Ignore GetDeviceGroupTemperature for another group $gId")
        this
    }
  }

  override def onSignal: PartialFunction[Signal, Behavior[DeviceGroup.Command]] = {
    case PostStop =>
      ctx.log.info(s"Device group actor $groupId stopped.")
      this
  }
}

object DeviceGroup {

  def apply(groupId: String): Behavior[Command] = {
    Behaviors.setup[Command](ctx => new DeviceGroup(groupId, ctx))
  }

  trait Command

  case class DeviceStopped(groupId: String, deviceId: String) extends Command

  case object Stop extends Command
}
