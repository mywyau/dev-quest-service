package models.languages

import io.circe.Decoder
import io.circe.Encoder

sealed trait Language

case object Python extends Language
case object Java extends Language
case object Rust extends Language
case object TypeScript extends Language
case object Scala extends Language
case object Sql extends Language

object Language {

  def fromString(str: String): Language =
    str match {
      case "Python" => Python
      case "Java" => Java
      case "Rust" => Rust
      case "TypeScript" => TypeScript
      case "Scala" => Scala
      case "Sql" => Sql
      case _ => throw new Exception(s"Unknown Language type: $str")
    }

  implicit val languageEncoder: Encoder[Language] =
    Encoder.encodeString.contramap {
      case Python => "Python"
      case Java => "Java"
      case Rust => "Rust"
      case TypeScript => "TypeScript"
      case Scala => "Scala"
      case Sql => "Sql"
    }

  implicit val languageDecoder: Decoder[Language] =
    Decoder.decodeString.emap {
      case "Python" => Right(Python)
      case "Java" => Right(Java)
      case "Rust" => Right(Rust)
      case "TypeScript" => Right(TypeScript)
      case "Scala" => Right(Scala)
      case "Sql" => Right(Sql)
      case other => Left(s"Invalid Language: $other")
    }
}
