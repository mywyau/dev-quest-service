package models.languages

import io.circe.Decoder
import io.circe.Encoder

sealed trait Language

case object Go extends Language
case object Java extends Language
case object JavaScript extends Language
case object Python extends Language
case object Ruby extends Language
case object Rust extends Language
case object Scala extends Language
case object Sql extends Language
case object TypeScript extends Language

object Language {

  def fromString(str: String): Language =
    str match {
      case "Go" => Go
      case "Java" => Java
      case "JavaScript" => JavaScript
      case "Python" => Python
      case "Ruby" => Ruby
      case "Rust" => Rust
      case "Scala" => Scala
      case "Sql" => Sql
      case "TypeScript" => TypeScript
      case _ => throw new Exception(s"Unknown Language type: $str")
    }

  implicit val languageEncoder: Encoder[Language] =
    Encoder.encodeString.contramap {
      case Go => "Go"
      case Java => "Java"
      case JavaScript => "JavaScript"
      case Python => "Python"
      case Ruby => "Ruby"
      case Rust => "Rust"
      case Scala => "Scala"
      case Sql => "Sql"
      case TypeScript => "TypeScript"
    }

  implicit val languageDecoder: Decoder[Language] =
    Decoder.decodeString.emap {
      case "Go" => Right(Go)
      case "Java" => Right(Java)
      case "JavaScript" => Right(JavaScript)
      case "Python" => Right(Python)
      case "Ruby" => Right(Ruby)
      case "Rust" => Right(Rust)
      case "Scala" => Right(Scala)
      case "Sql" => Right(Sql)
      case "TypeScript" => Right(TypeScript)
      case other => Left(s"Invalid Language: $other")
    }
}
