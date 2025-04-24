package middleware

import cats.effect._
import cats.syntax.all._
import com.auth0.jwt.interfaces.RSAKeyProvider
import io.circe._
import io.circe.parser._
import java.security.interfaces.RSAPublicKey
import java.security.spec.RSAPublicKeySpec
import java.security.KeyFactory
import java.util.Base64
import org.http4s.circe.CirceEntityCodec.circeEntityDecoder
import org.http4s.client.Client

class JwksKeyProvider[F[_] : Concurrent](jwksUrl: String, client: Client[F]) extends RSAKeyProvider {

  private var cache: Map[String, RSAPublicKey] = Map.empty

  private def base64UrlDecode(s: String): Array[Byte] =
    Base64.getUrlDecoder.decode(s)

  def loadJwks[F[_] : Concurrent](jwksUrl: String, client: Client[F]): F[Map[String, RSAPublicKey]] =
    client.expect[String](jwksUrl).flatMap { body =>
      parse(body).flatMap(_.hcursor.downField("keys").as[List[Json]]) match {
        case Left(err) => Concurrent[F].raiseError(new RuntimeException(s"JWKS parse error: $err"))
        case Right(keys) =>
          keys
            .flatMap { json =>
              for {
                kid <- json.hcursor.get[String]("kid").toOption
                n <- json.hcursor.get[String]("n").toOption
                e <- json.hcursor.get[String]("e").toOption
              } yield kid -> buildPublicKey(n, e)
            }
            .toMap
            .pure[F]
      }
    }

  private def buildPublicKey(n: String, e: String): RSAPublicKey = {
    val spec = new RSAPublicKeySpec(
      new java.math.BigInteger(1, base64UrlDecode(n)),
      new java.math.BigInteger(1, base64UrlDecode(e))
    )
    val factory = KeyFactory.getInstance("RSA")
    factory.generatePublic(spec).asInstanceOf[RSAPublicKey]
  }

  private def fetchKeys: F[Map[String, RSAPublicKey]] =
    client.expect[String](jwksUrl).flatMap { body =>
      parse(body).flatMap(_.hcursor.downField("keys").as[List[Json]]) match {
        case Left(err) => Concurrent[F].raiseError(new RuntimeException(s"JWKS parse error: $err"))
        case Right(keys) =>
          keys
            .flatMap { json =>
              for {
                kid <- json.hcursor.get[String]("kid").toOption
                n <- json.hcursor.get[String]("n").toOption
                e <- json.hcursor.get[String]("e").toOption
              } yield kid -> buildPublicKey(n, e)
            }
            .toMap
            .pure[F]
      }
    }
    
  override def getPublicKeyById(keyId: String): RSAPublicKey = ???

  def getPublicKeyByIdF(kid: String): F[RSAPublicKey] =
    cache.get(kid) match {
      case Some(key) => Concurrent[F].pure(key)
      case None =>
        for {
          keys <- fetchKeys
          _ <- Concurrent[F].pure { cache = keys }
          key <-
            keys
              .get(kid)
              .fold(
                Concurrent[F].raiseError[RSAPublicKey](
                  new RuntimeException(s"Unknown KID: $kid")
                )
              )(Concurrent[F].pure)
        } yield key
    }

  override def getPrivateKey = null
  override def getPrivateKeyId = null
}


class StaticJwksKeyProvider(keys: Map[String, RSAPublicKey]) extends RSAKeyProvider {
  override def getPublicKeyById(kid: String): RSAPublicKey =
    keys.getOrElse(kid, throw new RuntimeException(s"Unknown KID: $kid"))

  override def getPrivateKey = null
  override def getPrivateKeyId = null
}


object JwksKeyProvider {
  private def base64UrlDecode(s: String): Array[Byte] =
    Base64.getUrlDecoder.decode(s)

  private def buildPublicKey(n: String, e: String): RSAPublicKey = {
    val spec = new RSAPublicKeySpec(
      new java.math.BigInteger(1, base64UrlDecode(n)),
      new java.math.BigInteger(1, base64UrlDecode(e))
    )
    val factory = KeyFactory.getInstance("RSA")
    factory.generatePublic(spec).asInstanceOf[RSAPublicKey]
  }

  def loadJwks[F[_]: Concurrent](jwksUrl: String, client: Client[F]): F[Map[String, RSAPublicKey]] =
    client.expect[String](jwksUrl).flatMap { body =>
      parse(body).flatMap(_.hcursor.downField("keys").as[List[Json]]) match {
        case Left(err) => Concurrent[F].raiseError(new RuntimeException(s"JWKS parse error: $err"))
        case Right(keys) =>
          keys
            .flatMap { json =>
              for {
                kid <- json.hcursor.get[String]("kid").toOption
                n   <- json.hcursor.get[String]("n").toOption
                e   <- json.hcursor.get[String]("e").toOption
              } yield kid -> buildPublicKey(n, e)
            }
            .toMap
            .pure[F]
      }
    }
}