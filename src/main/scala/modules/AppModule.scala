// modules/AppModule.scala
package modules

import cats.effect.*
import cats.effect.std.Supervisor
import cats.Parallel
import configuration.AppConfig
import dev.profunktor.redis4cats.RedisCommands
import doobie.hikari.HikariTransactor
import org.http4s.client.Client
import org.typelevel.log4cats.Logger

object AppModule {

  def run[F[_] : Async : Logger : Temporal : Parallel](
    appConfig: AppConfig,
    transactor: HikariTransactor[F],
    redis: RedisCommands[F, String, String],
    httpClient: Client[F]
  ): Resource[F, Unit] =
    for {
      kafkaProducers <- KafkaModule.make[F](appConfig)

      // Run Kafka consumers
      // consumerStream <- KafkaConsumers.questCreatedStream[F](appConfig)
      // supervisor <- Supervisor[F].toResource
      // _ <- supervisor.supervise(consumerStream.compile.drain)

      // Run estimation finalizer job
      // _ <- supervisor.supervise(
      //   JobsModule
      //     .estimationFinalizerStream[F](appConfig, transactor, kafkaProducers.questEstimationProducer)
      //     .compile
      //     .drain
      // )

      // Start HTTP server
      _ <- HttpModule.make[F](appConfig, transactor, redis, httpClient, kafkaProducers)
    } yield ()
}
