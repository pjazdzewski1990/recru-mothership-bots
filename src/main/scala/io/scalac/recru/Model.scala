package io.scalac.recru

object Color extends Enumeration {
  type Color = Value
  val Yellow, Orange, Red, blue, Green, Purple = Value
  def withNameOpt(s: String): Option[Color] =
    values.find(_.toString.toLowerCase == s.toLowerCase)
}

case class GameId(v: String) extends AnyVal