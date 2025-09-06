package controllers

import infrastructure.cache.*
import cats.data.Validated.Invalid
import cats.data.Validated.Valid
import cats.effect.kernel.Async
import cats.effect.Concurrent
import cats.implicits.*
import fs2.Stream
import io.circe.syntax.EncoderOps
import io.circe.Json
import models.database.UpdateSuccess
import models.responses.*
import models.stripe.StripeDevUserData
import models.Completed
import models.Failed
import models.InProgress
import models.NotStarted
import models.Review
import models.Submitted
import org.http4s.*
import org.http4s.circe.*
import org.http4s.dsl.Http4sDsl
import org.http4s.headers.`WWW-Authenticate`
import org.http4s.syntax.all.http4sHeaderSyntax
import org.http4s.Challenge
import org.typelevel.log4cats.Logger
import scala.concurrent.duration.*
import services.ProfileServiceAlgebra
import services.StripeRegistrationService

trait ProfileControllerAlgebra[F[_]] {
  def routes: HttpRoutes[F]
}

class ProfileControllerImpl[F[_] : Async : Concurrent : Logger](
  profileService: ProfileServiceAlgebra[F],
  stripeRegistrationService: StripeRegistrationService[F]
) extends Http4sDsl[F]
    with ProfileControllerAlgebra[F] {

  implicit val stripeDevUserDataDecoder: EntityDecoder[F, StripeDevUserData] = jsonOf[F, StripeDevUserData]

  val routes: HttpRoutes[F] = HttpRoutes.of[F] {

    case req @ GET -> Root / "profile" / "health" =>
      Logger[F].debug(s"[ProfileController] GET - Health check for backend ProfileController") *>
        Ok(GetResponse("/dev-quest-service/skill/health", "I am alive - ProfileController").asJson)

    case req @ GET -> Root / "profile" / "skill" / "language" / "data" / devId =>
      Logger[F].debug(s"[ProfileController] GET - Trying to get skill data for userId $devId") *>
        profileService.getSkillAndLanguageData(devId).flatMap {
          case Nil =>
            BadRequest(ErrorResponse("NO_PROFILE_SKILL_OR_LANGUAGE_DATA", s"No profile data found").asJson)
          case profileData =>
            Logger[F].debug(s"[ProfileController] GET - Successfully retrieved skill & language data for userId $devId, ${profileData.asJson}") *>
              Ok(profileData.asJson)
        }

    case req @ POST -> Root / "stripe" / "onboarding" =>
      for {
        _ <- Logger[F].debug(s"[ProfileController] POST - Trying to get stripe link for user")
        userData <- req.as[StripeDevUserData] // or decode from session/cookie.   // this is a simple json body to return the devId
        _ <- Logger[F].debug(s"[ProfileController] POST - UserData Recieved: $userData")
        link <- stripeRegistrationService.createAccountLink(userData.userId)
        resp <- {
          Logger[F].debug(s"[ProfileController] POST - Stripe account creation Link created generated: ${link.asJson}") *>
            Ok(link.asJson)
        }
      } yield resp

    case req @ POST -> Root / "stripe" / "onboarding" / "complete" =>
      for {
        userData <- req.as[StripeDevUserData] // or decode from session/cookie.   // this is a simple json body to return the devId
        _ <- stripeRegistrationService.fetchAndUpdateAccountDetails(userData.userId)
        resp <- Ok(Json.obj("status" -> Json.fromString("Stripe account status updated")))
      } yield resp
  }
}

object ProfileController {
  def apply[F[_] : Async : Concurrent](
    profileService: ProfileServiceAlgebra[F],
    stripeRegistrationService: StripeRegistrationService[F]
  )(implicit logger: Logger[F]): ProfileControllerAlgebra[F] =
    new ProfileControllerImpl[F](profileService, stripeRegistrationService)
}
