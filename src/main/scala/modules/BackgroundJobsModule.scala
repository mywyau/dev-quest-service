package modules

import cats.effect.*
import cats.effect.kernel.Clock as CEClock
import cats.syntax.all.*
import cats.NonEmptyParallel
import configuration.AppConfig
import doobie.hikari.HikariTransactor
import fs2.Stream
import jobs.EstimateServiceBuilder
import jobs.EstimationFinalizerJob
import org.typelevel.log4cats.Logger
import repositories.*
import scala.concurrent.duration.*
import scala.concurrent.duration.DurationInt
import scala.concurrent.duration.FiniteDuration
import services.*
import services.kafka.producers.QuestEstimationEventProducerAlgebra

object BackgroundJobsModule {

  def estimationFinalizerJobStream[F[_] : Async : Logger : Clock : NonEmptyParallel : Temporal](
    transactor: HikariTransactor[F],
    config: AppConfig,
    estimationProducer: QuestEstimationEventProducerAlgebra[F]
  ): Stream[F, Unit] = {

    val questRepo = QuestRepository[F](transactor)
    val expirationRepo = EstimationExpirationRepository[F](transactor)
    val estimateService = EstimateServiceBuilder.build(transactor, config, estimationProducer)

    val job = new EstimationFinalizerJob[F](questRepo, expirationRepo, estimateService)

    def schedule: Stream[F, Unit] =
      if (config.featureSwitches.localTesting)
        Stream.fixedRateStartImmediately[F](config.estimationConfig.intervalSeconds.seconds).evalMap(_ => job.run.handleErrorWith(Logger[F].warn(_)(s"Estimation job failed")))
      else
        scheduleAt6HourBuckets(job.run)

    def scheduleAt6HourBuckets(task: F[Unit]): Stream[F, Unit] = {
      import java.time.*
      import java.time.temporal.ChronoUnit
      val zone = ZoneId.systemDefault()

      def computeNextDelay: F[FiniteDuration] =
        CEClock[F].realTimeInstant.map { now =>
          val localNow = now.atZone(zone).toLocalDateTime
          val buckets = List(0, 6, 12, 18).map(h => localNow.toLocalDate.atTime(h, 0))
          val nextBucket = buckets.find(localNow.isBefore).getOrElse(localNow.toLocalDate.plusDays(1).atTime(0, 0))
          val delay = Duration.between(localNow, nextBucket)
          FiniteDuration(delay.toMillis, MILLISECONDS)
        }

      Stream.eval(computeNextDelay).flatMap { initialDelay =>
        Stream.sleep_[F](initialDelay) ++
          Stream.eval(task) ++
          Stream.fixedRateStartImmediately[F](6.hours).evalMap(_ => task)
      }
    }

    schedule.handleErrorWith(e => Stream.eval(Logger[F].warn(e)("Estimation job crashed")))
  }

  /**
   * Start all background jobs and keep them running while the Resource is
   * alive.
   */
  def runAll[F[_] : Async : Logger : Clock : NonEmptyParallel : Temporal](
    transactor: HikariTransactor[F],
    config: AppConfig,
    estimationProducer: QuestEstimationEventProducerAlgebra[F]
  ): Resource[F, Unit] =
    Resource.make {
      Concurrent[F].start(
        estimationFinalizerJobStream(transactor, config, estimationProducer).compile.drain
      )
    }(_.cancel).void
}
