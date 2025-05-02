package repository.quest

import cats.data.Validated.Valid
import cats.effect.IO
import cats.effect.Resource
import cats.implicits.*
import doobie.*
import doobie.implicits.*
import java.time.LocalDateTime
import models.database.*
import repository.fragments.quest.QuestRepoFragments.*
import shared.TransactorResource
import testData.QuestTestConstants.*
import testData.TestConstants.*
import weaver.GlobalRead
import weaver.IOSuite
import weaver.ResourceTag

class QuestRepositoryISpec(global: GlobalRead) extends IOSuite {

  type Res = QuestRepositoryImpl[IO]

  private def initializeSchema(transactor: TransactorResource): Resource[IO, Unit] =
    Resource.eval(
      createQuestTable.update.run.transact(transactor.xa).void *>
        resetQuestTable.update.run.transact(transactor.xa).void *>
        insertQuestData.update.run.transact(transactor.xa).void
    )

  def testQuestRequest(userId: String, businessId: String): CreateQuestRequest =
    CreateQuestRequest(
      userId = userId,
      businessId = businessId,
      businessName = Some("mikey_corp"),
      buildingName = Some("butter building"),
      floorNumber = Some("floor 1"),
      street = Some("Main street 123"),
      city = Some("New York"),
      country = Some("USA"),
      county = Some("fake county"),
      postcode = Some("123456"),
      latitude = Some(100.1),
      longitude = Some(-100.1)
    )

  def sharedResource: Resource[IO, QuestRepositoryImpl[IO]] = {
    val setup = for {
      transactor <- global.getOrFailR[TransactorResource]()
      questRepo = new QuestRepositoryImpl[IO](transactor.xa)
      createSchemaIfNotPresent <- initializeSchema(transactor)
    } yield questRepo

    setup
  }

  test(".findByQuestId() - should find and return the business address if business_id exists for a previously created business address") { questRepo =>

    val expectedResult =
      QuestPartial(
        userId = "USER001",
        businessId = "BUS001",
        buildingName = Some("Innovation Tower"),
        floorNumber = Some("5"),
        street = Some("123 Tech Street"),
        city = Some("San Francisco"),
        country = Some("USA"),
        county = Some("California"),
        postcode = Some("94105"),
        latitude = Some(37.774929),
        longitude = Some(-122.419416)
      )

    for {
      questOpt <- questRepo.findByQuestId("BUS001")
    } yield expect(questOpt == Some(expectedResult))
  }

  test(".deleteQuest() - should delete the business address if business_id exists for a previously existing business address") { questRepo =>

    val userId = "USER002"
    val businessId = "BUS002"

    val expectedResult =
      QuestPartial(
        userId = userId,
        businessId = businessId,
        buildingName = Some("Global Tower"),
        floorNumber = Some("12"),
        street = Some("456 Global Ave"),
        city = Some("New York"),
        country = Some("USA"),
        county = Some("New York"),
        postcode = Some("123456"),
        latitude = Some(40.712776),
        longitude = Some(-74.005974)
      )

    for {
      firstFindResult <- questRepo.findByQuestId(businessId)
      deleteResult <- questRepo.delete(businessId)
      afterDeletionFindResult <- questRepo.findByQuestId(businessId)
    } yield expect.all(
      firstFindResult == Some(expectedResult),
      deleteResult == Valid(DeleteSuccess),
      afterDeletionFindResult == None
    )
  }
}
