package io.scalac.recru

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import io.scalac.recru.bots.{NaughtyBot, UnpredictableBot, WalkerBot}

import scala.util.Random

object BotsApp extends App {
  println("Starting bots!")

  implicit val system = ActorSystem("clients")
  implicit val materializer = ActorMaterializer()
  implicit val executionContext = system.dispatcher

  val kafkaBootstrapServer = sys.env("KAFKA")
  val api = sys.env("API")

  println(s"Running bots with API: ${api} and Kafka: ${kafkaBootstrapServer}")

  val client = new PlayHttpComms(api)
  val r = new Random()

  system.actorOf(UnpredictableBot.props("alice", kafkaBootstrapServer, client, r))
  system.actorOf(UnpredictableBot.props("bob", kafkaBootstrapServer, client, r))

  system.actorOf(WalkerBot.props("celine", kafkaBootstrapServer, client))

  system.actorOf(NaughtyBot.props("dexter", kafkaBootstrapServer, client))
}
