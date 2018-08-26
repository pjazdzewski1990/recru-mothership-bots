package io.scalac.recru.bots

import akka.actor.{ActorLogging, Props}
import akka.kafka.scaladsl.Consumer.Control
import akka.stream.Materializer
import io.scalac.recru.Color.Color
import io.scalac.recru._

import scala.concurrent.duration._
import scala.util.control.NonFatal

object NaughtyBot {
  def props(usedName: String, kafkaAddress: String, client: HttpComms)
           (implicit mat: Materializer): Props =
    Props(new NaughtyBot(usedName, kafkaAddress, client)(mat))
}

//a behaviour where that prioritizes pushing back the leading player over moving forward
class NaughtyBot(usedName: String, kafkaAddress: String, client: HttpComms)
               (implicit mat: Materializer) extends BotBase(kafkaAddress)(mat) with ActorLogging {
  import BotBaseInternal._

  type BoardState = List[List[Color]] // outer list are the fields, inner are stacked pieces

  scheduleGameStart()

  def updateBoard(player: String, color: Color, move: Int, boardState: BoardState): BoardState = {
    val (fieldContainingColor, fieldContainingColorIdx) = boardState.zipWithIndex.find(_._1.contains(color)).get
    val fieldToPutTheColorIdx = Math.max(0, Math.min(boardState.size - 1, fieldContainingColorIdx + move))

    val (notMovingThisTurn, movingThisTurn) = if(fieldContainingColorIdx != 0 && fieldContainingColorIdx != boardState.size - 1) {
      fieldContainingColor.splitAt(fieldContainingColor.indexOf(color))
    } else {
      (fieldContainingColor.filter(_ != color), fieldContainingColor.filter(_ == color))
    }
    val boardWithColorRemoved: List[List[Color]] = boardState.patch(
      from = fieldContainingColorIdx,
      patch = Seq(notMovingThisTurn),
      replaced = 1)

    val oldValuesAtUpdateIdx = boardWithColorRemoved(fieldToPutTheColorIdx)
    val boardWithColorAddedAgain = boardWithColorRemoved.patch(
      from = fieldToPutTheColorIdx,
      patch = Seq(oldValuesAtUpdateIdx ++ movingThisTurn),
      replaced = 1)

    boardWithColorAddedAgain
  }

  def playingGameReceive(gameId: GameId, myColor: Color.Value, currentListener: Control, board: BoardState): Receive = {

    case NewTurnStarted(playerStartingTheTurn) if playerStartingTheTurn == usedName =>
      log.info("Player {} is doing his turn", usedName)
      val leadingEnemy = board
        .reverse // start looking from the end of the board
        .filter(_.nonEmpty) // but take only fields that have any pieces on them
        .headOption // take the first occupied field
        .filterNot(_.contains(myColor)) // but make sure we aren't on it
        .flatMap(_.headOption) // take the first piece as this will move the whole stack

      leadingEnemy match {
        case Some(rival) =>
          client.sendMove(playerName = usedName, gameId = gameId, rival, move = -1).map(_ => log.info(s"${usedName} ${rival} was moved back"))
        case None => //if there's no threatening rival we move ourselves forward
          client.sendMove(playerName = usedName, gameId = gameId, myColor, move = 1).map(_ => log.debug("Move was sent"))
      }

    case GameDidEnd(winners) =>
      if(winners.contains(usedName)) {
        log.info("We did win {}", winners)
      } else {
        log.info("We didn't win {}" , winners)
      }
      scheduleGameStart()
      context.become(waitingForGameReceive())

    case GameUpdated(player, color, move) =>
      val updatedBoard = updateBoard(player, color, move, board)
      val newState = playingGameReceive(gameId, myColor, currentListener, updatedBoard)
      context.become(newState)

    case _: GameStarted | _: NewTurnStarted =>
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
      import Color._
      log.info("{} Connected to {}, listening on {}", usedName, game, listenOn)
      val listenerControl: Control = buildListener(listenOn, game)
      val initialBoardState = List(Yellow, Orange, Red, Blue, Green, Purple) :: List.fill(9)(List.empty)

      val newState = playingGameReceive(game, color, listenerControl, initialBoardState)
      context.become(newState)
  }

  override def receive: Receive = waitingForGameReceive()
}
