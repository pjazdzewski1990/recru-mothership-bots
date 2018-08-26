package io.scalac.recru

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import io.scalac.recru.bots.WalkerBot

object BotsApp extends App {
  println("Starting bots!")

  implicit val system = ActorSystem("clients")
  implicit val materializer = ActorMaterializer()
  implicit val executionContext = system.dispatcher

  val kafkaBootstrapServer = sys.env("KAFKA")
  val api = sys.env("API")

  println(s"Running bots with API: ${api} and Kafka: ${kafkaBootstrapServer}")

  val client = new PlayHttpComms(api)
  system.actorOf(WalkerBot.props("alice", kafkaBootstrapServer, client))
  system.actorOf(WalkerBot.props("bob", kafkaBootstrapServer, client))
}
