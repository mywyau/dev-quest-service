package middleware

import cats.data.*
import cats.effect.*
import cats.syntax.all.*
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.interfaces.DecodedJWT
import doobie.hikari.HikariTransactor
import org.http4s.*
import org.http4s.client.Client
import org.http4s.dsl.Http4sDsl
import org.http4s.server.AuthMiddleware

object JwtAuth {

  def middleware[F[_] : Sync](algorithm: Algorithm, routes: AuthedRoutes[DecodedJWT, F]): HttpRoutes[F] = {
    val dsl = Http4sDsl[F]
    import dsl._

    val verifyToken: String => F[Either[String, DecodedJWT]] = token =>
      Sync[F].delay {
        Either
          .catchNonFatal {
            com.auth0.jwt.JWT.require(algorithm).build().verify(token)
          }
          .leftMap(_.getMessage)
      }

    val authUser: Kleisli[F, Request[F], Either[String, DecodedJWT]] = Kleisli { req =>
      req.headers.get[headers.Authorization] match {
        case Some(headers.Authorization(Credentials.Token(AuthScheme.Bearer, token))) =>
          verifyToken(token)
        case _ =>
          Sync[F].pure(Left("Missing or invalid Authorization header"))
      }
    }

    val onFailure: AuthedRoutes[String, F] = Kleisli(req => OptionT.liftF(Forbidden(s"Access denied: ${req.authInfo}")))

    AuthMiddleware(authUser, onFailure).apply(routes)
  }

  def routesWithAuth[F[_] : Sync](
    xa: HikariTransactor[F],
    client: Client[F],
    algorithm: Algorithm
  ): HttpRoutes[F] = {

    val authedRoutes: AuthedRoutes[DecodedJWT, F] = {
      val dsl = Http4sDsl[F]
      import dsl._

      AuthedRoutes.of[DecodedJWT, F] { case GET -> Root / "me" as jwt =>
        Ok(s"👤 Hello, ${jwt.getSubject}, roles: ${jwt.getClaim("roles").asList(classOf[String])}")
      }
    }

    middleware(algorithm, authedRoutes)
  }

}
