package io.scalac.recru

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer

object BotsApp extends App {
  println("Starting bots!")

  implicit val system = ActorSystem("clients")
  implicit val materializer = ActorMaterializer()
  implicit val executionContext = system.dispatcher

  val kafkaBootstrapServer = sys.env("KAFKA")
  val api = sys.env("API")

  println(s"Running bots with API: ${api} and Kafka: ${kafkaBootstrapServer}")

  val client = new PlayHttpComms(api)
  system.actorOf(RunnerPlayer.props("bob", kafkaBootstrapServer, client))
  system.actorOf(RunnerPlayer.props("joe", kafkaBootstrapServer, client))
}
