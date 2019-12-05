package vn.khanhpdt.akka_playground.examples.iot_device

import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import akka.actor.typed.{Behavior, PostStop, Signal}

class IotSupervisor(ctx: ActorContext[Nothing]) extends AbstractBehavior[Nothing](ctx) {
  ctx.log.info("Iot System started.")

  override def onMessage(msg: Nothing): Behavior[Nothing] = {
    Behaviors.unhandled
  }

  override def onSignal: PartialFunction[Signal, Behavior[Nothing]] = {
    case PostStop =>
      ctx.log.info("Iot System stopped.")
      this
  }
}

object IotSupervisor {
  def apply(): Behavior[Nothing] = {
    Behaviors.setup[Nothing](ctx => new IotSupervisor(ctx))
  }
}