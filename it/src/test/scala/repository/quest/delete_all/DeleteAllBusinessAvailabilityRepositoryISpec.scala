package repository.business

import cats.data.Validated.Valid
import cats.effect.IO
import cats.effect.Resource
import cats.implicits.*
import doobie.*
import doobie.implicits.*
import java.time.LocalDateTime
import java.time.LocalTime
import models.*
import models.business.availability.*
import models.database.*
import repositories.business.BusinessAvailabilityRepositoryImpl
import repository.fragments.business.BusinessAvailabilityRepoFragments.*
import shared.TransactorResource
import testData.BusinessTestConstants.*
import testData.TestConstants.*
import weaver.GlobalRead
import weaver.IOSuite
import weaver.ResourceTag

class DeleteAllBusinessAvailabilityRepositoryISpec(global: GlobalRead) extends IOSuite {

  type Res = BusinessAvailabilityRepositoryImpl[IO]

  private def initializeSchema(transactor: TransactorResource): Resource[IO, Unit] =
    Resource.eval(
      createBusinessAvailabilityTable.update.run.transact(transactor.xa).void *>
        resetBusinessAvailabilityTable.update.run.transact(transactor.xa).void *>
        insertBusinessAvailabilityData.update.run.transact(transactor.xa).void
    )

  def sharedResource: Resource[IO, BusinessAvailabilityRepositoryImpl[IO]] = {
    val setup = for {
      transactor <- global.getOrFailR[TransactorResource]()
      businessAvailabilityRepo = new BusinessAvailabilityRepositoryImpl[IO](transactor.xa)
      createSchemaIfNotPresent <- initializeSchema(transactor)
    } yield businessAvailabilityRepo

    setup
  }

  test(".deleteAll() - should delete all of the business availability data for a given businessId") { businessAvailabilityRepo =>

    val expectedUpdateResult = DeleteSuccess

    for {
      deleteResult <- businessAvailabilityRepo.deleteAll(businessId1)
    } yield expect.all(
      deleteResult == Valid(expectedUpdateResult)
    )
  }
}
