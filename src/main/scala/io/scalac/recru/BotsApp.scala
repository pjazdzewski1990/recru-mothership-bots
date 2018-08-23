package io.scalac.recru

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer

object BotsApp extends App {
  println("Starting bots!")

  implicit val system = ActorSystem("clients")
  implicit val materializer = ActorMaterializer()
  implicit val executionContext = system.dispatcher

  val client = new PlayHttpComms("http://localhost:8081/")
  system.actorOf(RunnerPlayer.props("bob", client))
  system.actorOf(RunnerPlayer.props("joe", client))
}
