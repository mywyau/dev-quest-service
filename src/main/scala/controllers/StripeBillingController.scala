// controllers/StripeBillingController.scala
package controllers

import cats.effect.*
import cats.syntax.all.*
import org.http4s.*
import org.http4s.dsl.Http4sDsl
import org.http4s.dsl.Http4sDsl.*
import org.http4s.implicits.*
import org.typelevel.ci.CIString
import org.typelevel.log4cats.Logger
import services.stripe.*
import services.UserPricingPlanServiceAlgebra

trait StripeBillingWebhookControllerAlgebra[F[_]] {
  def routes: HttpRoutes[F]
}

final class StripeBillingWebhookControllerImpl[F[_] : Async : Logger](
  stripeBillingService: StripeBillingServiceAlgebra[F],
  plans: UserPricingPlanServiceAlgebra[F]
) extends StripeBillingWebhookControllerAlgebra[F] {

  private val dsl = new Http4sDsl[F] {}
  import dsl._

  private def handle(evt: StripeEvent): F[Unit] =
    Logger[F].debug(
      s"[Webhook] ${evt.eventType} userHint=${evt.userIdHint.getOrElse("-")} " +
        s"customer=${evt.customerId.getOrElse("-")} sub=${evt.subscriptionId.getOrElse("-")} " +
        s"status=${evt.status.getOrElse("-")}"
    ) *> plans.applyStripeWebhook(evt)
  val routes: HttpRoutes[F] = HttpRoutes.of[F] { case req @ POST -> Root / "billing" / "webhook" =>
    for {
      payload <- req.as[String]
      sigOpt = req.headers.get(CIString("Stripe-Signature")).map(_.head.value)
      sig <- Async[F].fromOption(sigOpt, new IllegalArgumentException("Missing Stripe-Signature header"))
      evt <- stripeBillingService.verifyAndParseEvent(payload, sig)
      _ <- handle(evt) // upsert + invalidate
      resp <- Ok()
    } yield resp
  }
}

object StripeBillingWebhookController {
  def apply[F[_] : Async : Concurrent](
    stripeBillingService: StripeBillingServiceAlgebra[F],
    plans: UserPricingPlanServiceAlgebra[F]
  )(implicit logger: Logger[F]): StripeBillingWebhookControllerAlgebra[F] =
    new StripeBillingWebhookControllerImpl[F](stripeBillingService, plans)
}
