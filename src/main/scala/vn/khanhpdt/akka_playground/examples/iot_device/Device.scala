package vn.khanhpdt.akka_playground.examples.iot_device

import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior, PostStop, Signal}

class Device(ctx: ActorContext[Device.Command], groupId: String, deviceId: String)
  extends AbstractBehavior[Device.Command](ctx) {

  ctx.log.info(s"Device actor $groupId-$deviceId starting...")

  import Device._

  private var lastTemperature: Option[Double] = None

  override def onMessage(msg: Device.Command): Behavior[Device.Command] = {
    msg match {
      case ReadTemperature(requestId, replyTo) =>
        replyTo ! RespondTemperature(requestId, deviceId, lastTemperature)
        this
      case RecordTemperature(id, temperature, replyTo) =>
        lastTemperature = Some(temperature)
        replyTo ! TemperatureRecorded(id)
        this
      case Stop =>
        Behaviors.stopped
    }
  }

  override def onSignal: PartialFunction[Signal, Behavior[Device.Command]] = {
    case PostStop =>
      ctx.log.info(s"Device actor $groupId-$deviceId stopped.")
      this
  }
}

object Device {

  def apply(groupId: String, deviceId: String): Behavior[Command] = {
    Behaviors.setup[Command](ctx => new Device(ctx, groupId, deviceId))
  }

  sealed trait Command

  case class ReadTemperature(requestId: Int, replyTo: ActorRef[RespondTemperature]) extends Command

  case class RespondTemperature(requestId: Int, deviceId: String, temperature: Option[Double])

  case class RecordTemperature(requestId: Int, temperature: Double, replyTo: ActorRef[TemperatureRecorded]) extends Command

  case class TemperatureRecorded(requestId: Int)

  case object Stop extends Command

}
