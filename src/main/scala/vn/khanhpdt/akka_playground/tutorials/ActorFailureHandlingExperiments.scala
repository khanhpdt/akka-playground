package vn.khanhpdt.akka_playground.tutorials

import akka.actor.typed._
import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}

object SupervisingActor {
  def apply(): Behavior[String] = Behaviors.setup(ctx => new SupervisingActor(ctx))
}

class SupervisingActor(ctx: ActorContext[String]) extends AbstractBehavior[String](ctx) {
  private val child = ctx.spawn(
    Behaviors.supervise(SupervisedActor()).onFailure(SupervisorStrategy.restart),
    name = "supervisedActor"
  )

  override def onMessage(msg: String): Behavior[String] = {
    msg match {
      case "fail child" =>
        println("make child failed")
        child ! "fail"
        this
    }
  }
}

object SupervisedActor {
  def apply(): Behavior[String] = Behaviors.setup(ctx => new SupervisedActor(ctx))
}

class SupervisedActor(ctx: ActorContext[String]) extends AbstractBehavior[String](ctx) {
  override def onMessage(msg: String): Behavior[String] = {
    msg match {
      case "fail" =>
        println("supervised actor fails now")
        throw new RuntimeException("i failed now")
    }
  }

  override def onSignal: PartialFunction[Signal, Behavior[String]] = {
    case PostStop =>
      println("supervised actor stopped")
      this
    case PreRestart =>
      println("supervised actor will be restarted")
      this
  }
}

object Main3 {
  def apply(): Behavior[String] = Behaviors.setup(ctx => new Main3(ctx))
}

class Main3(ctx: ActorContext[String]) extends AbstractBehavior[String](ctx) {
  override def onMessage(msg: String): Behavior[String] = {
    msg match {
      case "start" =>
        val supervisor = ctx.spawn(SupervisingActor(), "supervisor")
        supervisor ! "fail child"
        this
    }
  }
}

object ActorFailureHandlingExperiments extends App {
  val sys = ActorSystem(Main3(), "ActorFailureHandlingExperiments")
  sys ! "start"
}
