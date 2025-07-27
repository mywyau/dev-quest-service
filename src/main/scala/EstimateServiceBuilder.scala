
// import cats.effect.*
// import cats.NonEmptyParallel
// import configuration.AppConfig
// import controllers.*
// import doobie.hikari.HikariTransactor
// import java.net.URI
// import org.http4s.client.Client
// import org.typelevel.log4cats.Logger
// import repositories.*
// import services.*
// import services.LevelService

// object EstimateServiceBuilder {
//   def build[F[_]: Concurrent: Logger](xa: HikariTransactor[F], config: AppConfig): EstimateServiceAlgebra[F] = {
//     val userRepo = UserDataRepositoryImpl[F](xa)
//     val estimateRepo = EstimateRepositoryImpl[F](xa)
//     val questRepo = QuestRepositoryImpl[F](xa)
//     val levelService = LevelServiceImpl[F](xa)
//     EstimateService[F](config, userRepo, estimateRepo, questRepo, levelService)
//   }
// }