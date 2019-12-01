package vn.khanhpdt.akka_playground

import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import akka.actor.typed.{ActorSystem, Behavior, PostStop, Signal}

object Actor1 {
  def apply(): Behavior[String] = Behaviors.setup(ctx => new Actor1(ctx))
}

class Actor1(ctx: ActorContext[String]) extends AbstractBehavior[String](ctx) {
  println("first started")

  ctx.spawn(Actor2(), "second")

  override def onMessage(msg: String): Behavior[String] = {
    msg match {
      case "stop" => Behaviors.stopped
    }
  }

  override def onSignal: PartialFunction[Signal, Behavior[String]] = {
    case PostStop =>
      println("first stopped")
      this
  }
}

object Actor2 {
  def apply(): Behavior[String] = Behaviors.setup(ctx => new Actor2(ctx))
}

class Actor2(ctx: ActorContext[String]) extends AbstractBehavior[String](ctx) {
  println("second started")

  override def onMessage(msg: String): Behavior[String] = {
    Behaviors.unhandled
  }

  override def onSignal: PartialFunction[Signal, Behavior[String]] = {
    case PostStop =>
      println("second stopped")
      this
  }
}

object Main2 {
  def apply(): Behavior[String] = Behaviors.setup(ctx => new Main2(ctx))
}

class Main2(ctx: ActorContext[String]) extends AbstractBehavior[String](ctx) {

  override def onMessage(msg: String): Behavior[String] = {
    msg match {
      case "start" =>
        val actor1 = ctx.spawn(Actor1(), "first")
        actor1 ! "stop"
        this
    }
  }
}

object ActorLifeCycleExperiments extends App {
  val sys = ActorSystem(Main2(), "ActorLifeCycleExperiments")
  sys ! "start"
}
