package vn.khanhpdt.akka_playground.examples.iot_device

import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors, TimerScheduler}
import akka.actor.typed.{ActorRef, Behavior, PostStop, Signal}
import scala.collection.mutable
import scala.concurrent.duration.FiniteDuration

class DeviceGroupTemperatureQuery(
  context: ActorContext[DeviceGroupTemperatureQuery.Command],
  devices: Map[String, ActorRef[Device.Command]],
  replyTo: ActorRef[DeviceManager.GetDeviceGroupTemperatureResult],
  timers: TimerScheduler[DeviceGroupTemperatureQuery.Command],
  timeout: FiniteDuration
) extends AbstractBehavior[DeviceGroupTemperatureQuery.Command](context) {

  import DeviceGroupTemperatureQuery._
  import DeviceManager.{
    DeviceQueryResult,
    DeviceNotAvailable,
    DeviceTemperatureAvailable,
    DeviceTemperatureNotAvailable,
    DeviceQueryTimedOut,
    GetDeviceGroupTemperatureResult
  }

  context.log.info("DeviceGroupTemperatureQuery started.")

  timers.startSingleTimer(QueryTimedOut, QueryTimedOut, timeout)

  private val respondTemperatureAdapter = context.messageAdapter(WrappedRespondTemperature.apply)

  private var waitingDevices = devices.keySet

  private val repliesSoFar = new mutable.HashMap[String, DeviceQueryResult]()

  private var requestId: Int = _

  override def onMessage(msg: Command): Behavior[Command] = {
    msg match {
      case Query(reqId) =>
        requestId = reqId

        devices.foreach { device =>
          context.watchWith(device._2, DeviceStopped(device._1))
        }

        devices.values.foreach { device =>
          device ! Device.ReadTemperature(requestId = 1, respondTemperatureAdapter)
        }

        this

      case DeviceStopped(deviceId) =>
        if (!repliesSoFar.contains(deviceId)) {
          repliesSoFar += deviceId -> DeviceNotAvailable
        }
        if (waitingDevices.contains(deviceId)) {
          waitingDevices -= deviceId
        }

        checkIfDone()

      case WrappedRespondTemperature(respondTemperature) =>
        respondTemperature.temperature match {
          case Some(value) =>
            repliesSoFar += respondTemperature.deviceId -> DeviceTemperatureAvailable(value)
          case None =>
            repliesSoFar += respondTemperature.deviceId -> DeviceTemperatureNotAvailable
        }

        waitingDevices -= respondTemperature.deviceId

        checkIfDone()

      case QueryTimedOut =>
        waitingDevices.foreach { waitingDevice =>
          repliesSoFar += waitingDevice -> DeviceQueryTimedOut
        }
        waitingDevices = Set.empty

        done()
    }
  }

  private def checkIfDone(): Behavior[Command] = {
    if (waitingDevices.isEmpty) done()
    else this
  }

  private def done(): Behavior[Command] = {
    replyTo ! GetDeviceGroupTemperatureResult(requestId, repliesSoFar.toMap)
    Behaviors.stopped
  }

  override def onSignal: PartialFunction[Signal, Behavior[Command]] = {
    case PostStop =>
      context.log.info("DeviceGroupTemperatureQuery stopped")
      this
  }
}

object DeviceGroupTemperatureQuery {

  def apply(
    devices: Map[String, ActorRef[Device.Command]],
    timeout: FiniteDuration,
    replyTo: ActorRef[DeviceManager.GetDeviceGroupTemperatureResult]
  ): Behavior[Command] = {
    Behaviors.setup(ctx => {
      Behaviors.withTimers(timers => {
        new DeviceGroupTemperatureQuery(ctx, devices, replyTo, timers, timeout)
      })
    })
  }

  sealed trait Command

  case class Query(requestId: Int) extends Command

  case object QueryTimedOut extends Command

  case class DeviceStopped(deviceId: String) extends Command

  case class WrappedRespondTemperature(res: Device.RespondTemperature) extends Command

}
