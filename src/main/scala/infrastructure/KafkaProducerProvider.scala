// configuration/kafka/KafkaProducerProvider.scala
package infrastructure

import cats.effect.kernel.Resource
import cats.effect.Async
import fs2.kafka.*

object KafkaProducerProvider {

  private def parseAcks(s: String): Acks =
    s.trim.toLowerCase match {
      case "0" | "none" => Acks.Zero
      case "1" | "one" | "leader" => Acks.One   
      case "-1" | "all" => Acks.All
      case other => Acks.All // sensible default
    }

  def make[F[_] : Async](
    bootstrap: String,
    clientId: String,
    acks: String,
    lingerMs: Int,
    retries: Int
  ): Resource[F, KafkaProducer[F, String, String]] = {

    val settings =
      ProducerSettings[F, String, String]
        .withBootstrapServers(bootstrap)
        .withClientId(clientId)
        .withAcks(parseAcks(acks))
        // .withAcks(Acks.fromString(acks).getOrElse(Acks.All))
        .withProperty("linger.ms", lingerMs.toString)
        .withProperty("retries", retries.toString)
      // Key/Value are Strings; serializers are inferred by fs2-kafka defaults

    KafkaProducer.resource(settings)
  }
}
