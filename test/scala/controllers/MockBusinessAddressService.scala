package controllers

import cats.data.Validated.Valid
import cats.data.ValidatedNel
import cats.effect.IO
import models.business.address.BusinessAddressPartial
import models.business.address.CreateBusinessAddressRequest
import models.business.address.UpdateBusinessAddressRequest
import models.database.*
import services.business.BusinessAddressServiceAlgebra

class MockBusinessAddressService(userAddressData: Map[String, BusinessAddressPartial]) extends BusinessAddressServiceAlgebra[IO] {

  override def getByBusinessId(businessId: String): IO[Option[BusinessAddressPartial]] =
    userAddressData.get(businessId) match {
      case Some(address) => IO.pure(Some(address))
      case None => IO.pure(None)
    }

  override def createAddress(request: CreateBusinessAddressRequest): IO[ValidatedNel[DatabaseErrors, DatabaseSuccess]] =
    IO.pure(Valid(CreateSuccess))

  override def update(businessId: String, request: UpdateBusinessAddressRequest): IO[ValidatedNel[DatabaseErrors, DatabaseSuccess]] =
    IO.pure(Valid(UpdateSuccess))

  override def delete(businessId: String): IO[ValidatedNel[DatabaseErrors, DatabaseSuccess]] =
    IO.pure(Valid(DeleteSuccess))
}
