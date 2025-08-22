package models.pricing

import io.circe.generic.semiauto.deriveDecoder
import io.circe.generic.semiauto.deriveEncoder
import io.circe.Decoder
import io.circe.Encoder
import io.circe.Json
import java.time.Instant
import java.time.LocalDateTime
import scala.util.Try

// Minimal Instant encoders (ISO-8601)
// If you already add `circe-java8` dependency, you can remove these.
object TimeCodecs:
  given Encoder[Instant] = Encoder.encodeString.contramap(_.toString)
  given Decoder[Instant] = Decoder.decodeString.emap { s =>
    Try(Instant.parse(s)).toEither.left.map(_.getMessage)
  }
  given Encoder[LocalDateTime] = Encoder.encodeString.contramap(_.toString)
  given Decoder[LocalDateTime] = Decoder.decodeString.emap { s =>
    Try(LocalDateTime.parse(s)).toEither.left.map(_.getMessage)
  }
