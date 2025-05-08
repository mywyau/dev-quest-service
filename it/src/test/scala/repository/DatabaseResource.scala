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

  // private def fetchSchemaQuery: Fragment =
  //   sql"""
  //     SELECT column_name, data_type, is_nullable
  //     FROM information_schema.columns
  //     WHERE table_name = 'user_login_details'
  //   """

  // private def printSchema(xa: Transactor[IO]): Resource[IO, Unit] =
  //   Resource.eval(
  //     fetchSchemaQuery.query[(String, String, String)]
  //       .to[List]
  //       .transact(xa)
  //       .flatMap { schema =>
  //         IO {
  //           println("Table Schema for 'user_login_details':")
  //           schema.foreach { case (name, typ, nullable) =>
  //             println(s"Column: $name, Type: $typ, Nullable: $nullable")
  //           }
  //         }
  //       }
  //   )

  // private def testInsertQuery: Update0 =
  //   sql"""
  //     INSERT INTO user_login_details (
  //       user_id,
  //       username,
  //       password_hash,
  //       email,
  //       role,
  //       created_at,
  //       updated_at
  //     ) VALUES (
  //       'test_user_id',
  //       'test_user',
  //       'hashed_password',
  //       'test@example.com',
  //       'Wanderer',
  //       CURRENT_TIMESTAMP,
  //       CURRENT_TIMESTAMP
  //     )
  //   """.update

  // private def testInsert(xa: Transactor[IO]): Resource[IO, Unit] =
  //   Resource.eval(
  //     testInsertQuery.run.transact(xa).attempt.flatMap {
  //       case Right(_) => IO(println("Test insert succeeded"))
  //       case Left(e) => IO(println(s"Test insert failed: ${e.getMessage}"))
  //     }
  //   )

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
