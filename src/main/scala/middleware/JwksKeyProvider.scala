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


class StaticJwksKeyProvider(keys: Map[String, RSAPublicKey]) extends RSAKeyProvider {
  override def getPublicKeyById(kid: String): RSAPublicKey =
    keys.getOrElse(kid, throw new RuntimeException(s"Unknown KID: $kid"))

  override def getPrivateKey = null
  override def getPrivateKeyId = null
}


object JwksKeyProvider {
  import models.{JwkKey, JwksResponse}
  import org.http4s.Uri
  import org.http4s.circe.CirceEntityCodec.circeEntityDecoder

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

  def loadJwks[F[_]: Concurrent](jwksUrl: String, client: Client[F]): F[Map[String, RSAPublicKey]] = for {
    uri  <- Uri.fromString(jwksUrl).liftTo[F]
    jwks <- client.expect[JwksResponse](uri)
  } yield jwks.keys.flatMap { key =>
    Option.when(key.kid != null && key.n != null && key.e != null) {
      key.kid -> buildPublicKey(key.n, key.e)
    }
  }.toMap
}
