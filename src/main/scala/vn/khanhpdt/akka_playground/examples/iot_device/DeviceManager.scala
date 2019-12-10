package vn.khanhpdt.akka_playground.examples.iot_device

import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}

import scala.collection.mutable

class DeviceManager(ctx: ActorContext[DeviceManager.Command]) extends AbstractBehavior[DeviceManager.Command](ctx) {

  import DeviceManager._

  private val deviceGroups = new mutable.HashMap[String, ActorRef[DeviceGroup.Command]]()

  override def onMessage(msg: Command): Behavior[Command] = {
    msg match {
      case regMsg@RegisterDevice(_, groupId, _, _) =>
        val deviceGroup = deviceGroups.getOrElseUpdate(groupId, {
          val group = ctx.spawn(DeviceGroup(groupId), s"deviceGroup-$groupId")
          ctx.watchWith(group, DeviceGroupStopped(groupId))
          group
        })
        deviceGroup ! regMsg
        this
      case listMsg@ListAllDevices(requestId, groupId, replyTo) =>
        deviceGroups.get(groupId) match {
          case Some(ref) => ref ! listMsg
          case None => replyTo ! DeviceList(requestId, groupId, Set.empty[String])
        }
        this
      case DeviceGroupStopped(groupId) =>
        deviceGroups.remove(groupId)
        this
      case GetDeviceGroup(requestId, groupId, replyTo) =>
        replyTo ! GetDeviceGroupResponse(requestId, deviceGroups.get(groupId))
        this
    }
  }
}

object DeviceManager {

  def apply(): Behavior[Command] = {
    Behaviors.setup[Command](ctx => new DeviceManager(ctx))
  }

  sealed trait Command

  case class RegisterDevice(requestId: Int, groupId: String, deviceId: String, replyTo: ActorRef[DeviceRegistered])
    extends Command with DeviceGroup.Command

  case class DeviceRegistered(requestId: Int, deviceRef: ActorRef[Device.Command])

  case class ListAllDevices(requestId: Int, groupId: String, replyTo: ActorRef[DeviceList])
    extends Command with DeviceGroup.Command

  case class DeviceList(requestId: Int, groupId: String, deviceIds: Set[String])

  case class DeviceGroupStopped(groupId: String) extends Command

  case class GetDeviceGroup(requestId: Int, groupId: String, replyTo: ActorRef[GetDeviceGroupResponse]) extends Command

  case class GetDeviceGroupResponse(requestId: Int, deviceGroup: Option[ActorRef[DeviceGroup.Command]])

}