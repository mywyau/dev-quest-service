import cats.data._
import cats.effect._
import cats.syntax.all._
import com.auth0.jwt._
import com.auth0.jwt.interfaces.DecodedJWT
import com.auth0.jwt.algorithms.Algorithm
import org.http4s._
import org.http4s.dsl.Http4sDsl
import org.http4s.server.AuthMiddleware

object JwtAuth {
  def middleware[F[_]: Sync](
      client: Client[F],
      jwksUrl: String
  ): AuthMiddleware[F, DecodedJWT] = {

    val dsl = Http4sDsl[F]
    import dsl._

    val keyProvider = new JwksKeyProvider[F](jwksUrl, client)
    val algorithm = Algorithm.RSA256(keyProvider)

    val validateJwt: String => F[Either[String, DecodedJWT]] = token =>
      Sync[F].delay {
        Either.catchNonFatal {
          JWT.require(algorithm).build().verify(token)
        }.leftMap(_.getMessage)
      }

    val extractToken: Kleisli[F, Request[F], Either[String, DecodedJWT]] =
      Kleisli { req =>
        req.headers.get[headers.Authorization] match {
          case Some(headers.Authorization(Credentials.Token(AuthScheme.Bearer, token))) =>
            validateJwt(token)
          case _ =>
            Sync[F].pure(Left("Missing or invalid Authorization header"))
        }
      }

    val onAuthFailure: AuthedRoutes[String, F] =
      Kleisli(req => OptionT.liftF(Forbidden(req.authInfo)))

    AuthMiddleware(extractToken, onAuthFailure)
  }
}
