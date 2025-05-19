package repository

import cats.effect.IO
import cats.effect.Resource
import configuration.models.*
import configuration.BaseAppConfig
import doobie.*
import doobie.hikari.HikariTransactor
import doobie.implicits.*
import scala.concurrent.ExecutionContext
import shared.TransactorResource
import weaver.GlobalResource
import weaver.GlobalWrite

object DatabaseResource extends GlobalResource with BaseAppConfig {

  def executionContextResource: Resource[IO, ExecutionContext] =
    ExecutionContexts.fixedThreadPool(4)

  def transactorResource(postgresqlConfig: PostgresqlConfig, ce: ExecutionContext): Resource[IO, HikariTransactor[IO]] =
    HikariTransactor.newHikariTransactor[IO](
      driverClassName = "org.postgresql.Driver",
      url = s"jdbc:postgresql://${postgresqlConfig.host}:${postgresqlConfig.port}/${postgresqlConfig.dbName}",
      user = postgresqlConfig.username,
      pass = postgresqlConfig.password,
      connectEC = ce
    )

  def sharedResources(global: GlobalWrite): Resource[IO, Unit] =
    for {
      appConfig <- configResource
      postgresqlConfig <- postgresqlConfigResource(appConfig)
      postgresqlHost <- Resource.eval {
        IO.pure(sys.env.getOrElse("DB_HOST", postgresqlConfig.host))
      }
      postgresqlPort <- Resource.eval {
        IO.pure(sys.env.get("DB_PORT").flatMap(p => scala.util.Try(p.toInt).toOption).getOrElse(postgresqlConfig.port))
      }
      ce <- executionContextResource
      xa <- transactorResource(postgresqlConfig.copy(host = postgresqlHost, port = postgresqlPort), ce)
      _ <- global.putR(TransactorResource(xa))
      // Uncomment the following lines to enable schema printing and test insertion during initialization
      // _ <- printSchema(xa)
      // _ <- testInsert(xa)
    } yield ()
}
