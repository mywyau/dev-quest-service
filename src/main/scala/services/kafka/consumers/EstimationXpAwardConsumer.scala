// services/kafka/consumers/XpAwardConsumer.scala
package services.kafka.consumers

import cats.effect.*
import cats.syntax.all.*
import fs2.kafka.*
import fs2.Stream
import io.circe.*
import io.circe.generic.auto.*
import io.circe.parser.*
import models.kafka.QuestEstimationFinalized
import org.typelevel.log4cats.Logger
import services.XpServiceAlgebra

object EstimationXpAwardConsumer {

  final case class Settings(
    topic: String = "quest.estimation.finalized.v1",
    groupId: String = "xp-award-service",
    bootstrapServers: String
  )

  def resource[F[_] : Async : Logger](
    settings: Settings,
    xpService: XpServiceAlgebra[F]
  ): Resource[F, Stream[F, Unit]] = {

    val consumerSettings =
      ConsumerSettings[F, String, String]
        .withBootstrapServers(settings.bootstrapServers)
        .withGroupId(settings.groupId)
        .withAutoOffsetReset(AutoOffsetReset.Earliest)
        .withEnableAutoCommit(false)

    val stream: Stream[F, Unit] =
      KafkaConsumer
        .stream(consumerSettings)
        .evalTap(_.subscribeTo(settings.topic))
        .flatMap(_.stream) // <-- flatten
        .evalMap { committable =>
          val rawJson = committable.record.value
          decode[QuestEstimationFinalized](rawJson) match {
            case Left(err) =>
              Logger[F].error(err)(s"[XpAwardConsumer] decode failed: $rawJson") *>
                committable.offset.commit
            case Right(event) =>
              Logger[F].info(s"[XpAwardConsumer] quest=${event.questId} received") *>
                xpService.awardEstimationXp(event) *>
                committable.offset.commit
          }
        }
        .handleErrorWith(e => Stream.eval(Logger[F].error(e)("[XpAwardConsumer] stream crashed")))

    Resource.pure(stream)
  }
}
