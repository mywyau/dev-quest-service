package routes

import cats.effect.*
import cats.NonEmptyParallel
import controllers.*
import controllers.business.BusinessAddressController
import controllers.business.BusinessContactDetailsController
import controllers.business.BusinessListingController
import controllers.business.BusinessListingControllerImpl
import controllers.business.BusinessSpecificationsController
import controllers.BaseController
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
import repositories.business.BusinessAddressRepository
import repositories.business.BusinessContactDetailsRepository
import repositories.business.BusinessListingRepository
import repositories.business.BusinessSpecificationsRepository
import repositories.desk.DeskListingRepository
import repositories.desk.DeskPricingRepository
import repositories.desk.DeskSpecificationsRepository
import repositories.office.OfficeAddressRepository
import repositories.office.OfficeContactDetailsRepository
import repositories.office.OfficeListingRepository
import repositories.office.OfficeSpecificationsRepository
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

  def baseRoutes[F[_] : Concurrent : Temporal : NonEmptyParallel : Async : Logger](transactor: HikariTransactor[F]): HttpRoutes[F] = {

    val baseController = BaseController[F]()

    baseController.routes
  }

  def deskListingRoutes[F[_] : Concurrent : Temporal : NonEmptyParallel : Async : Logger](transactor: HikariTransactor[F]): HttpRoutes[F] = {

    val deskListingRepo = DeskListingRepository(transactor)
    val deskListingService = DeskListingService(deskListingRepo)
    val deskListingController = new DeskListingControllerImpl[F](deskListingService)

    deskListingController.routes
  }

  def deskSpecificationsRoutes[F[_] : Concurrent : Temporal : NonEmptyParallel : Async : Logger](transactor: HikariTransactor[F]): HttpRoutes[F] = {

    val deskSpecificationsRepo = DeskSpecificationsRepository(transactor)
    val deskSpecificationsService = DeskSpecificationsService(deskSpecificationsRepo)
    val deskSpecificationsController = new DeskSpecificationsControllerImpl[F](deskSpecificationsService)

    deskSpecificationsController.routes
  }

  def deskPricingRoutes[F[_] : Concurrent : Temporal : NonEmptyParallel : Async : Logger](transactor: HikariTransactor[F]): HttpRoutes[F] = {

    val deskPricingRepo = DeskPricingRepository(transactor)
    val deskPricingService = DeskPricingService(deskPricingRepo)
    val deskPricingController = new DeskPricingControllerImpl[F](deskPricingService)

    deskPricingController.routes
  }

  def officeAddressRoutes[F[_] : Concurrent : Temporal : NonEmptyParallel : Async : Logger](transactor: HikariTransactor[F]): HttpRoutes[F] = {

    val officeAddressRepository = OfficeAddressRepository(transactor)
    val officeAddressService = OfficeAddressService(officeAddressRepository)
    val officeAddressController = OfficeAddressController(officeAddressService)

    officeAddressController.routes
  }

  def officeContactDetailsRoutes[F[_] : Concurrent : Temporal : NonEmptyParallel : Async : Logger](transactor: HikariTransactor[F]): HttpRoutes[F] = {

    val officeContactDetailsRepository = OfficeContactDetailsRepository(transactor)
    val officeContactDetailsService = OfficeContactDetailsService(officeContactDetailsRepository)
    val officeContactDetailsController = OfficeContactDetailsController(officeContactDetailsService)

    officeContactDetailsController.routes
  }

  def officeSpecificationsRoutes[F[_] : Concurrent : Temporal : NonEmptyParallel : Async : Logger](transactor: HikariTransactor[F]): HttpRoutes[F] = {

    val officeSpecificationsRepository = OfficeSpecificationsRepository(transactor)
    val officeSpecificationsService = OfficeSpecificationsService(officeSpecificationsRepository)
    val officeSpecificationsController = OfficeSpecificationsController(officeSpecificationsService)

    officeSpecificationsController.routes
  }

  def businessAddressRoutes[F[_] : Concurrent : Temporal : NonEmptyParallel : Async : Logger](transactor: HikariTransactor[F]): HttpRoutes[F] = {

    val businessAddressRepository = BusinessAddressRepository(transactor)
    val businessAddressService = BusinessAddressService(businessAddressRepository)
    val businessAddressController = BusinessAddressController(businessAddressService)

    businessAddressController.routes
  }

  def businessContactDetailsRoutes[F[_] : Concurrent : Temporal : NonEmptyParallel : Async : Logger](transactor: HikariTransactor[F]): HttpRoutes[F] = {

    val businessContactDetailsRepository = BusinessContactDetailsRepository(transactor)
    val businessContactDetailsService = BusinessContactDetailsService(businessContactDetailsRepository)
    val businessContactDetailsController = BusinessContactDetailsController(businessContactDetailsService)

    businessContactDetailsController.routes
  }

  def businessSpecificationsRoutes[F[_] : Concurrent : Temporal : NonEmptyParallel : Async : Logger](transactor: HikariTransactor[F]): HttpRoutes[F] = {

    val businessSpecificationsRepository = BusinessSpecificationsRepository(transactor)
    val businessSpecificationsService = BusinessSpecificationsService(businessSpecificationsRepository)
    val businessSpecificationsController = BusinessSpecificationsController(businessSpecificationsService)

    businessSpecificationsController.routes
  }

  def officeListingRoutes[F[_] : Concurrent : Temporal : NonEmptyParallel : Async : Logger](transactor: HikariTransactor[F]): HttpRoutes[F] = {

    val officeListingRepository = OfficeListingRepository(transactor)
    val officeListingService = OfficeListingService(officeListingRepository)
    val officeListingController = OfficeListingController(officeListingService)

    officeListingController.routes
  }

  def businessListingRoutes[F[_] : Concurrent : Temporal : NonEmptyParallel : Async : Logger](transactor: HikariTransactor[F]): HttpRoutes[F] = {

    val businessListingRepository = BusinessListingRepository(transactor)

    val businessListingService = BusinessListingService(businessListingRepository)
    val businessListingController = BusinessListingController(businessListingService)

    businessListingController.routes
  }
}
