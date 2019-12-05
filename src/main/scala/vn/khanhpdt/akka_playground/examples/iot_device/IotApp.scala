package vn.khanhpdt.akka_playground.examples.iot_device

import akka.actor.typed.ActorSystem

object IotApp {

  def main(args: Array[String]): Unit = {
    ActorSystem[Nothing](IotSupervisor(), "iot-system")
  }

}
