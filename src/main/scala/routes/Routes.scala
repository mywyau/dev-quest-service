package routes

import cats.NonEmptyParallel
import cats.effect.*
import controllers.*
import controllers.BaseController
import controllers.business.BusinessAddressController
import controllers.business.BusinessContactDetailsController
import controllers.business.BusinessListingController
import controllers.business.BusinessListingControllerImpl
import controllers.business.BusinessSpecificationsController
import controllers.desk.DeskListingControllerImpl
import controllers.desk.DeskPricingControllerImpl
import controllers.desk.DeskSpecificationsControllerImpl
import controllers.office.OfficeAddressController
import controllers.office.OfficeContactDetailsController
import controllers.office.OfficeListingController
import controllers.office.OfficeSpecificationsController
import doobie.hikari.HikariTransactor
import org.http4s.HttpRoutes
import org.typelevel.log4cats.Logger
import repositories.*

import services.*
import services.business.BusinessAddressService
import services.business.BusinessContactDetailsService
import services.business.BusinessListingService
import services.business.BusinessSpecificationsService
import services.desk.DeskListingService
import services.desk.DeskPricingService
import services.desk.DeskSpecificationsService
import services.office.OfficeAddressService
import services.office.OfficeContactDetailsService
import services.office.OfficeListingService
import services.office.OfficeSpecificationsService

object Routes {

  def baseRoutes[F[_] : Concurrent : Logger](): HttpRoutes[F] = {

    val baseController = BaseController()

    baseController.routes
  }

  def businessListingRoutes[F[_] : Concurrent : Temporal : NonEmptyParallel : Async : Logger](transactor: HikariTransactor[F]): HttpRoutes[F] = {

    val businessListingRepository = BusinessListingRepository(transactor)

    val businessListingService = BusinessListingService(businessListingRepository)
    val businessListingController = BusinessListingController(businessListingService)

    businessListingController.routes
  }
}
