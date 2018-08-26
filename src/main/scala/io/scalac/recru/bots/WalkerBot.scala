package io.scalac.recru.bots

import akka.actor.{ActorLogging, Props}
import akka.kafka.scaladsl.Consumer.Control
import akka.stream.Materializer
import io.scalac.recru._

import scala.concurrent.duration._
import scala.util.control.NonFatal

object WalkerBot {
  def props(usedName: String, kafkaAddress: String, client: HttpComms)
           (implicit mat: Materializer): Props =
    Props(new WalkerBot(usedName, kafkaAddress, client)(mat))
}

//a simple player behaviour where it takes his color and always goes forward
class WalkerBot(usedName: String, kafkaAddress: String, client: HttpComms)
               (implicit mat: Materializer) extends BotBase(kafkaAddress)(mat) with ActorLogging {
  import BotBaseInternal._

  scheduleGameStart()

  def playingGameReceive(gameId: GameId, color: Color.Value, currentListener: Control): Receive = {

    case NewTurnStarted(playerStartingTheTurn) if playerStartingTheTurn == usedName =>
      log.info("Player {} is doing his turn", usedName)
      client.sendMove(playerName = usedName, gameId = gameId, color, move = 1).map(_ => log.debug("Move was sent"))

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
