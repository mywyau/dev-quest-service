package modules

// modules/JobsModule.scala

import cats.effect.*
import cats.syntax.all.catsSyntaxApplicativeError
import cats.NonEmptyParallel
import configuration.AppConfig
import doobie.hikari.HikariTransactor
import fs2.Stream
import jobs.EstimateServiceBuilder
import org.typelevel.log4cats.Logger
import scala.concurrent.duration.DurationInt
import services.kafka.producers.QuestEstimationEventProducerAlgebra

object JobsModule {

  def estimationFinalizerStream[F[_] : Concurrent : Temporal : NonEmptyParallel : Async : Logger : Clock](
    cfg: AppConfig,
    xa: HikariTransactor[F],
    questEstimationEventProducer: QuestEstimationEventProducerAlgebra[F]
  ): Stream[F, Unit] = {

    val estimateService = EstimateServiceBuilder.build[F](xa, cfg, questEstimationEventProducer)

    val runOnce =
      estimateService
        .finalizeExpiredEstimations()
        .handleErrorWith(e => Logger[F].warn(e)("estimation finalizer failed"))

    val cadence: Stream[F, Unit] =
      if (cfg.featureSwitches.localTesting)
        Stream.fixedRateStartImmediately[F](cfg.estimationConfig.intervalSeconds.seconds).evalMap(_ => runOnce)
      else
        Stream.awakeEvery[F](6.hours).evalMap(_ => runOnce)

    Stream.eval(runOnce) ++ cadence
  }
}
