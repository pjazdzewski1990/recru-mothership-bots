package io.scalac.recru

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer

object BotsApp extends App {
  println("Starting bots!")

  implicit val system = ActorSystem("clients")
  implicit val materializer = ActorMaterializer()
  implicit val executionContext = system.dispatcher

  val kafkaAddress = "docker.for.mac.host.internal:29092" //TODO: read it from docker envs
  val client = new PlayHttpComms("http://localhost:8081/") //TODO: read it from docker envs
  system.actorOf(RunnerPlayer.props("bob", kafkaAddress, client))
  system.actorOf(RunnerPlayer.props("joe", kafkaAddress, client))
}
