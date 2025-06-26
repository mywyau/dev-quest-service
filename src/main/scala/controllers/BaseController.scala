package controllers

import cats.data.Validated.Valid
import cats.effect.Concurrent
import cats.effect.IO
import cats.implicits.*
import io.circe.syntax.EncoderOps
import models.database.CreateSuccess
import models.responses.*
import org.http4s.*
import org.http4s.circe.*
import org.http4s.dsl.Http4sDsl
import org.typelevel.log4cats.Logger
import models.database.CreateSuccess

trait BaseControllerAlgebra[F[_]] {
  def routes: HttpRoutes[F]
}

class BaseControllerImpl[F[_] : Concurrent : Logger]() extends BaseControllerAlgebra[F] with Http4sDsl[F] {

  override val routes: HttpRoutes[F] = HttpRoutes.of[F] {
    case req @ GET -> Root / "health" =>
      Logger[F].debug(s"[BaseControllerImpl] GET - Health check for backend service: ${GetResponse("success", "I am alive").asJson}") *>
        Ok(GetResponse("success", "I am alive").asJson)
  }

}

object BaseController {
  def apply[F[_] : Concurrent]()(implicit logger: Logger[F]): BaseControllerAlgebra[F] =
    new BaseControllerImpl[F]()
}
