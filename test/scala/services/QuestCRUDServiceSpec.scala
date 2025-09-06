package services

import cats.data.NonEmptyList
import cats.data.Validated.Invalid
import cats.data.Validated.Valid
import cats.effect.IO
import configuration.ConfigReader
import configuration.ConfigReaderAlgebra
import mocks.*
import mocks.producers.*
import models.Completed
import models.Steel
import models.database.*
import models.languages.*
import models.quests.*
import services.QuestCRUDService
import services.QuestCRUDServiceImpl
import services.constants.QuestServiceConstants.*
import testData.TestConstants.*
import weaver.SimpleIOSuite
import models.kafka.SuccessfulWrite

object QuestCRUDServiceSpec extends SimpleIOSuite with ServiceSpecBase {

  val configReader: ConfigReaderAlgebra[IO] = ConfigReader[IO]
  val mockUserDataRepository = MockUserDataRepository
  val mockLevelService = MockLevelService
  val mockHoursWorkedRepository = MockHoursWorkedRepository
  val mockQuestEventProducer = MockQuestEventProducer()

  test(".acceptQuest() - when the number of quests a dev has accepted is <= 5, return success") {

    val existingQuestForUser = testQuest(clientId1, Some(devId1), questId1)

    val mockQuestRepository = new MockQuestRepository(countActiveQuests = 5, existingQuest = Map(questId1 -> existingQuestForUser))

    for {
      appConfig <- configReader.loadAppConfig
      service = QuestCRUDService(appConfig, mockQuestRepository, mockUserDataRepository, mockHoursWorkedRepository, mockLevelService, mockQuestEventProducer)
      result <- service.acceptQuest(questId1, devId1)
    } yield expect(result == Valid(UpdateSuccess))
  }

  test(".acceptQuest() - when the number of quests a dev has accepted is > 5, return success") {

    val existingQuestForUser = testQuest(clientId1, Some(devId1), questId1)

    val mockQuestRepository = new MockQuestRepository(countActiveQuests = 6, existingQuest = Map(questId1 -> existingQuestForUser))

    for {
      appConfig <- configReader.loadAppConfig
      service = QuestCRUDService(appConfig, mockQuestRepository, mockUserDataRepository, mockHoursWorkedRepository, mockLevelService, mockQuestEventProducer)
      result <- service.acceptQuest(questId1, devId1)
    } yield expect(result == Invalid(NonEmptyList.one(TooManyActiveQuestsError)))
  }

  test(".getByQuestId() - when there is an existing quest details given a questId should return the correct address details - Right(address)") {

    val existingQuestForUser = testQuest(clientId1, Some(devId1), questId1)

    val mockQuestRepository = new MockQuestRepository(existingQuest = Map(questId1 -> existingQuestForUser))

    for {
      appConfig <- configReader.loadAppConfig
      service = QuestCRUDService(appConfig, mockQuestRepository, mockUserDataRepository, mockHoursWorkedRepository, mockLevelService, mockQuestEventProducer)
      result <- service.getByQuestId(questId1)
    } yield expect(result == Some(existingQuestForUser))
  }

  test(".create() - should create a quest, and producer successfully writes an event") {

    val existingQuestForUser = testQuest(clientId1, Some(devId1), questId1)

    val mockQuestRepository = new MockQuestRepository(existingQuest = Map(questId1 -> existingQuestForUser))

    val createQuestPartial =
      CreateQuestPartial(
        rank = Steel,
        title = "Some title",
        description = Some("description"),
        acceptanceCriteria = "acceptanceCriteria",
        tags = Seq(Python, Scala, Rust)
      )

    for {
      appConfig <- configReader.loadAppConfig
      service = QuestCRUDService(appConfig, mockQuestRepository, mockUserDataRepository, mockHoursWorkedRepository, mockLevelService, mockQuestEventProducer)
      result <- service.create(createQuestPartial, clientId1)
    } yield expect(result == Valid(SuccessfulWrite))
  }

  test(".update() - should update the details of a given quest") {

    val existingQuestForUser = testQuest(clientId1, Some(devId1), questId1)

    val mockQuestRepository = new MockQuestRepository(existingQuest = Map(clientId1 -> existingQuestForUser))

    val updateQuestPartial =
      UpdateQuestPartial(
        rank = Steel,
        title = "Some title",
        description = Some("description"),
        acceptanceCriteria = Some("acceptanceCriteria")
      )

    for {
      appConfig <- configReader.loadAppConfig
      service = QuestCRUDService(appConfig, mockQuestRepository, mockUserDataRepository, mockHoursWorkedRepository, mockLevelService, mockQuestEventProducer)
      result <- service.update(questId1, updateQuestPartial)
    } yield expect(result == Valid(UpdateSuccess))
  }

  test(".update() - should update the details of a given quest") {

    val existingQuestForUser = testQuest(clientId1, Some(devId1), questId1)

    val mockQuestRepository = new MockQuestRepository(existingQuest = Map(clientId1 -> existingQuestForUser))

    val updateQuestPartial =
      UpdateQuestPartial(
        rank = Steel,
        title = "Some title",
        description = Some("description"),
        acceptanceCriteria = Some("acceptanceCriteria")
      )

    for {
      appConfig <- configReader.loadAppConfig
      service = QuestCRUDService(appConfig, mockQuestRepository, mockUserDataRepository, mockHoursWorkedRepository, mockLevelService, mockQuestEventProducer)
      result <- service.update(questId1, updateQuestPartial)
    } yield expect(result == Valid(UpdateSuccess))
  }

  test(".updateStatus() - should update the status of a given quest, given a questId and QuestStatus") {

    val existingQuestForUser = testQuest(clientId1, Some(devId1), questId1)

    val mockQuestRepository = new MockQuestRepository(existingQuest = Map(clientId1 -> existingQuestForUser))

    for {
      appConfig <- configReader.loadAppConfig
      service = QuestCRUDService(appConfig, mockQuestRepository, mockUserDataRepository, mockHoursWorkedRepository, mockLevelService, mockQuestEventProducer)
      result <- service.updateStatus(questId1, Completed)
    } yield expect(result == Valid(UpdateSuccess))
  }
}
