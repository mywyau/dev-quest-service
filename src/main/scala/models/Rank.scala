package models

import io.circe.Decoder
import io.circe.Encoder

sealed trait Rank

case object UnknownRank extends Rank
case object Bronze extends Rank
case object Iron extends Rank
case object Steel extends Rank
case object Mithril extends Rank
case object Adamantite extends Rank
case object Runic extends Rank
case object Demon extends Rank
case object Ruinous extends Rank
case object Aether extends Rank

object Rank {

  def fromString(str: String): Rank =
    str match {
      case "UnknownRank" => UnknownRank
      case "Bronze" => Bronze
      case "Iron" => Iron
      case "Steel" => Steel
      case "Mithril" => Mithril
      case "Adamantite" => Adamantite
      case "Runic" => Runic
      case "Demon" => Demon
      case "Ruinous" => Ruinous
      case "Aether" => Aether
      case _ => throw new Exception(s"Unknown Rank type: $str")
    }

  implicit val rankEncoder: Encoder[Rank] =
    Encoder.encodeString.contramap {
      case UnknownRank => "UnknownRank"
      case Bronze => "Bronze"
      case Iron => "Iron"
      case Steel => "Steel"
      case Mithril => "Mithril"
      case Adamantite => "Adamantite"
      case Runic => "Runic"
      case Demon => "Demon"
      case Ruinous => "Ruinous"
      case Aether => "Aether"
    }

  implicit val rankDecoder: Decoder[Rank] =
    Decoder.decodeString.emap {
      case "UnknownRank" => Right(UnknownRank)
      case "Bronze" => Right(Bronze)
      case "Iron" => Right(Iron)
      case "Steel" => Right(Steel)
      case "Mithril" => Right(Mithril)
      case "Adamantite" => Right(Adamantite)
      case "Runic" => Right(Runic)
      case "Demon" => Right(Demon)
      case "Ruinous" => Right(Ruinous)
      case "Aether" => Right(Aether)
      case other => Left(s"Invalid Rank: $other")
    }
}
