package vn.khanhpdt.akka_playground.tutorials

import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import akka.actor.typed.{ActorSystem, Behavior}

object PrintMyActorRefActor {
  def apply(): Behavior[String] = {
    Behaviors.setup(ctx => new PrintMyActorRefActor(ctx))
  }
}

class PrintMyActorRefActor(ctx: ActorContext[String]) extends AbstractBehavior[String](ctx) {
  override def onMessage(msg: String): Behavior[String] = {
    msg match {
      case "printit" =>
        val secondRef = ctx.spawn(Behaviors.empty[String], "second-actor")
        println(s"Second: $secondRef")
        this
    }
  }
}

object Main1 {
  def apply(): Behavior[String] = {
    Behaviors.setup(ctx => new Main1(ctx))
  }
}

class Main1(ctx: ActorContext[String]) extends AbstractBehavior[String](ctx) {
  override def onMessage(msg: String): Behavior[String] = {
    msg match {
      case "start" =>
        val firstRef = ctx.spawn(PrintMyActorRefActor(), "first-actor")
        println(s"First: $firstRef")
        firstRef ! "printit"
        this
    }
  }
}

object ActorHierarchyExperiments extends App {
  val testSystem = ActorSystem(Main1(), "testSystem")
  testSystem ! "start"
}
