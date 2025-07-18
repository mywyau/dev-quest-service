package models.languages

import io.circe.Decoder
import io.circe.Encoder

sealed trait Language

case object C extends Language
case object CPlusPlus extends Language
case object CSharp extends Language
case object Go extends Language
case object Java extends Language
case object JavaScript extends Language
case object Kotlin extends Language
case object PHP extends Language
case object Python extends Language
case object Ruby extends Language
case object Rust extends Language
case object Scala extends Language
case object Sql extends Language
case object Swift extends Language
case object TypeScript extends Language

object Language {

  def fromString(str: String): Language =
    str match {
      case "C" => C
      case "CPlusPlus" => CPlusPlus
      case "CSharp" => CSharp
      case "Go" => Go
      case "Java" => Java
      case "JavaScript" => JavaScript
      case "Kotlin" => Kotlin
      case "PHP" => PHP
      case "Python" => Python
      case "Ruby" => Ruby
      case "Rust" => Rust
      case "Scala" => Scala
      case "Sql" => Sql
      case "Swift" => Swift
      case "TypeScript" => TypeScript
      case _ => throw new Exception(s"Unknown Language type: $str")
    }

  implicit val languageEncoder: Encoder[Language] =
    Encoder.encodeString.contramap {
      case C => "C"
      case CPlusPlus => "CPlusPlus"
      case CSharp => "CSharp"
      case Go => "Go"
      case Java => "Java"
      case JavaScript => "JavaScript"
      case Kotlin => "Kotlin"
      case PHP => "PHP"
      case Python => "Python"
      case Ruby => "Ruby"
      case Rust => "Rust"
      case Scala => "Scala"
      case Sql => "Sql"
      case Swift => "Swift"
      case TypeScript => "TypeScript"
    }

  implicit val languageDecoder: Decoder[Language] =
    Decoder.decodeString.emap {
      case "C" => Right(C)
      case "CPlusPlus" => Right(CPlusPlus)
      case "CSharp" => Right(CSharp)
      case "Go" => Right(Go)
      case "Java" => Right(Java)
      case "JavaScript" => Right(JavaScript)
      case "Kotlin" => Right(Kotlin)
      case "PHP" => Right(PHP)
      case "Python" => Right(Python)
      case "Ruby" => Right(Ruby)
      case "Rust" => Right(Rust)
      case "Scala" => Right(Scala)
      case "Sql" => Right(Sql)
      case "Swift" => Right(Swift)
      case "TypeScript" => Right(TypeScript)
      case other => Left(s"Invalid Language: $other")
    }
}
