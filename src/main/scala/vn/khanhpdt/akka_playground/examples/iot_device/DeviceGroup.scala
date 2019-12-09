package vn.khanhpdt.akka_playground.examples.iot_device

import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior, PostStop, Signal}

import scala.collection.mutable

class DeviceGroup(groupId: String, ctx: ActorContext[DeviceGroup.Command]) extends AbstractBehavior[DeviceGroup.Command](ctx) {

  import DeviceGroup._

  ctx.log.info(s"Device group actor [$groupId] starting...")

  private val devices = new mutable.HashMap[String, ActorRef[Device.Command]]()

  override def onMessage(msg: DeviceGroup.Command): Behavior[DeviceGroup.Command] = {
    msg match {
      case RegisterDevice(requestId, `groupId`, deviceId, replyTo) =>
        val deviceActor = devices.getOrElseUpdate(deviceId, {
          ctx.spawn(Device(groupId, deviceId), s"device-$deviceId")
        })
        replyTo ! DeviceRegistered(requestId, deviceActor)
        this
      case RegisterDevice(_, gId, _, _) =>
        ctx.log.warn(s"Ignore RegisterDevice request for another group [$gId]")
        this
      case ListAllDevices(requestId, replyTo) =>
        replyTo ! DeviceList(requestId, devices.keySet.toSet)
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

  sealed trait Command

  case class RegisterDevice(requestId: Int, groupId: String, deviceId: String, replyTo: ActorRef[DeviceRegistered]) extends Command

  case class DeviceRegistered(requestId: Int, deviceRef: ActorRef[Device.Command])

  case class ListAllDevices(requestId: Int, replyTo: ActorRef[DeviceList]) extends Command

  case class DeviceList(requestId: Int, deviceIds: Set[String])

}
