package io.scalac.recru

import akka.NotUsed
import com.typesafe.scalalogging.Logger
import akka.stream.Materializer
import play.api.libs.ws.StandaloneWSRequest
import play.api.libs.ws.ahc.StandaloneAhcWSClient
import spray.json.{JsNumber, JsObject, JsString, JsonParser}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

object HttpComms {
  case class ConnectedToGame(game: GameId, color: Color.Value, listenOn: String)
}

trait HttpComms {
  import HttpComms._
  def connect(nameToUse: String): Future[ConnectedToGame]
  def sendMove(playerName: String, gameId: GameId, color: Color.Value, move: Int): Future[NotUsed]
}

class PlayHttpComms(baseUrl: String)
                   (implicit mat: Materializer, ec: ExecutionContext) extends HttpComms {
  import HttpComms._
  import play.api.libs.ws.DefaultBodyWritables._

  val wsClient = StandaloneAhcWSClient()

  val log = Logger(classOf[PlayHttpComms])

  override def connect(nameToUse: String): Future[HttpComms.ConnectedToGame] = {
    val url = baseUrl + "game/"
    val b = JsObject("name" -> JsString(nameToUse)).prettyPrint

    wsClient.url(url).addHttpHeaders("Content-Type" -> "application/json").post(b).flatMap {
      case r if r.status == 200 =>
        parseConnectReponse(r)
      case r =>
        Future.failed(new IllegalArgumentException(s"Couldn't connect to game ${r.status}: ${r.body}"))
    }
  }

  private def parseConnectReponse(r: StandaloneWSRequest#Self#Response) = {
    JsonParser(r.body) match {
      case JsObject(fields) =>
        val connectedOpt = for {
          gameId <- fields.get("gameId").flatMap {
            case JsString(v) => Some(GameId(v))
            case _ => None
          }
          color <- fields.get("secretColor").flatMap {
            case JsString(v) => Color.withNameOpt(v)
            case _ => None
          }.headOption
          listen <- r.header("x-listen-on")
        } yield {
          ConnectedToGame(game = gameId, color = color, listenOn = listen)
        }

        connectedOpt.map(Future.successful).getOrElse(
          Future.failed(new IllegalArgumentException(s"Not enough data in ${fields}"))
        )
      case _ =>
        Future.failed(new Exception("Cannot parse " + r.body))
    }
  }

  override def sendMove(playerName: String, gameId: GameId, color: Color.Value, move: Int): Future[NotUsed] = {
    val url = baseUrl + "game/" + gameId.v
    val b = JsObject(
      "name" -> JsString(playerName),
      "color" -> JsString(color.toString),
      "move" -> JsNumber(move)
    ).prettyPrint

    val post = wsClient.url(url)
      .addHttpHeaders("Content-Type" -> "application/json")
      .put(b)
      .flatMap {
        case r if r.status == 200 =>
          Future.successful(NotUsed)
        case r =>
          Future.failed(new IllegalArgumentException(s"Couldn't make a move ${r.status}: ${r.body}"))
      }

    post.recover {
      case NonFatal(err) =>
        log.error(s"Was not able to send the move ${err.getMessage}", err)
    }
    post
  }
}