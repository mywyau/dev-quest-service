package modules

// modules/DatabaseModule.scala

import cats.effect.*
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import configuration.AppConfig
import doobie.hikari.HikariTransactor
import doobie.util.ExecutionContexts
import org.typelevel.log4cats.Logger

object DatabaseModule {

  def make[F[_] : Async : Logger](appConfig: AppConfig): Resource[F, HikariTransactor[F]] = {

    val dbConfig = appConfig.postgresqlConfig

    val dbHost = sys.env.getOrElse("DB_HOST", dbConfig.host)
    val dbUser = sys.env.getOrElse("DB_USER", dbConfig.username)
    val dbPassword = sys.env.getOrElse("DB_PASSWORD", dbConfig.password)
    val dbName = sys.env.getOrElse("DB_NAME", dbConfig.dbName)
    val dbPort = sys.env.getOrElse("DB_PORT", dbConfig.port.toString)

    val dbUrl = s"jdbc:postgresql://$dbHost:$dbPort/$dbName"
    val driverClassName = "org.postgresql.Driver"

    val hikariConfig = new HikariConfig()
    hikariConfig.setJdbcUrl(dbUrl)
    hikariConfig.setUsername(dbUser)
    hikariConfig.setPassword(dbPassword)
    hikariConfig.setMaximumPoolSize(dbConfig.maxPoolSize)
    hikariConfig.setDriverClassName(driverClassName)

    val dataSource = new HikariDataSource(hikariConfig)

    for {
      ce <- ExecutionContexts.fixedThreadPool[F](dbConfig.maxPoolSize)
      transactor <- HikariTransactor.newHikariTransactor[F](
        driverClassName,
        dbUrl,
        dbUser,
        dbPassword,
        ce
      )
    } yield transactor
  }
}
