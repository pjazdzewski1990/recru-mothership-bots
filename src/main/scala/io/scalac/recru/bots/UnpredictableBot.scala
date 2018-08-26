package io.scalac.recru.bots

import akka.actor.{ActorLogging, Props}
import akka.kafka.scaladsl.Consumer.Control
import akka.stream.Materializer
import io.scalac.recru.{Color, _}

import scala.concurrent.duration._
import scala.util.Random
import scala.util.control.NonFatal

object UnpredictableBot {
  def props(usedName: String, kafkaAddress: String, client: HttpComms, rand: Random)
           (implicit mat: Materializer): Props =
    Props(new UnpredictableBot(usedName, kafkaAddress, client, rand)(mat))
}

//a simple player behaviour where it makes random actions
class UnpredictableBot(usedName: String, kafkaAddress: String, client: HttpComms, rand: Random)
               (implicit mat: Materializer) extends BotBase(kafkaAddress)(mat) with ActorLogging {
  import BotBaseInternal._

  scheduleGameStart()

  def randomColor(): Color.Value = rand.nextInt(6) match {
    case 0 => Color.Yellow
    case 1 => Color.Orange
    case 2 => Color.Red
    case 3 => Color.Blue
    case 4 => Color.Green
    case _ => Color.Purple
  }

  def randomMove(): Int = {
    val move = rand.nextInt(2) + 1
    if(rand.nextBoolean()) move else -move
  }

  def playingGameReceive(gameId: GameId, color: Color.Value, currentListener: Control): Receive = {

    case NewTurnStarted(playerStartingTheTurn) if playerStartingTheTurn == usedName =>
      log.info("Player {} is doing his turn", usedName)
      client.sendMove(playerName = usedName, gameId = gameId, randomColor(), move = randomMove()).map(_ => log.debug("Move was sent"))

    case GameDidEnd(winners) =>
      if(winners.contains(usedName)) {
        log.info("We did win {}", winners)
      } else {
        log.info("We didn't win {}" , winners)
      }
      scheduleGameStart()
      context.become(waitingForGameReceive())

    case _: GameStarted | _: NewTurnStarted | _: GameUpdated =>
    // do nothing

    case InvalidEvent =>
    // do nothing
  }

  def waitingForGameReceive(): Receive = {
    case ConnectToGame =>
      log.info("Connecting to the game!")
      client
        .connect(usedName)
        .map(g => self ! ListenForGameEvents(g.game, g.color, g.listenOn))
        .recover {
          case NonFatal(ex) =>
            log.error(ex,"Failed joining with {}", ex.getMessage)
            context.system.scheduler.scheduleOnce(10.second)(self ! ConnectToGame) // retry in a moment
        }

    case ListenForGameEvents(game, color, listenOn) =>
      log.info("{} Connected to {}, listening on {}", usedName, game, listenOn)
      val listenerControl: Control = buildListener(listenOn, game)
      val newState = playingGameReceive(game, color, listenerControl)
      context.become(newState)
  }

  override def receive: Receive = waitingForGameReceive()
}
