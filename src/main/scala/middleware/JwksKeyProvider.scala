package middleware

import cats.effect._
import cats.syntax.all._
import io.circe._
import io.circe.parser._
import org.http4s.client.Client
import com.auth0.jwt.interfaces.RSAKeyProvider
import java.security.interfaces.RSAPublicKey
import java.security.spec.RSAPublicKeySpec
import java.security.KeyFactory
import java.util.Base64

class JwksKeyProvider[F[_]: Sync](jwksUrl: String, client: Client[F]) extends RSAKeyProvider {

  private var cache: Map[String, RSAPublicKey] = Map.empty

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

  private def fetchKeys: F[Map[String, RSAPublicKey]] =
    client.expect[String](jwksUrl).flatMap { body =>
      parse(body).flatMap(_.hcursor.downField("keys").as[List[Json]]) match {
        case Left(err) => Sync[F].raiseError(new RuntimeException(s"JWKS parse error: $err"))
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

  override def getPublicKeyById(kid: String): RSAPublicKey =
    cache.getOrElse(kid, {
      val keys = fetchKeys.unsafeRunSync() // You may replace this with memoization later
      cache = keys
      keys.getOrElse(kid, throw new RuntimeException("KID not found in JWKS"))
    })

  override def getPrivateKey = null
  override def getPrivateKeyId = null
}
