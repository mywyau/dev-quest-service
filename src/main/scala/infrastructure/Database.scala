package infrastructure

import cats.effect.Async
import cats.effect.Resource
import configuration.AppConfig
import doobie.hikari.HikariTransactor
import doobie.util.ExecutionContexts

object Database {

  def transactor[F[_] : Async](appConfig: AppConfig): Resource[F, HikariTransactor[F]] = {
    
    val dbHost = sys.env.getOrElse("DB_HOST", appConfig.postgresqlConfig.host)
    val dbUser = sys.env.getOrElse("DB_USER", appConfig.postgresqlConfig.username)
    val dbPassword = sys.env.getOrElse("DB_PASSWORD", appConfig.postgresqlConfig.password)
    val dbName = sys.env.getOrElse("DB_NAME", appConfig.postgresqlConfig.dbName)
    val dbPort = sys.env.getOrElse("DB_PORT", appConfig.postgresqlConfig.port.toString)

    val dbUrl = s"jdbc:postgresql://$dbHost:$dbPort/$dbName"
    val driverClassName = "org.postgresql.Driver"

    for {
      ce <- ExecutionContexts.fixedThreadPool(32) // DB connection thread pool
      xa <- HikariTransactor.newHikariTransactor[F](
        driverClassName,
        dbUrl,
        dbUser,
        dbPassword,
        ce
      )
    } yield xa
  }
}
