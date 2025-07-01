package services

import cats.data.Validated.Valid
import cats.effect.IO
import configuration.ConfigReader
import configuration.ConfigReaderAlgebra
import mocks.*
import models.Steel
import models.database.*
import models.languages.*
import models.quests.*
import models.Completed
import services.QuestCRUDService
import services.QuestCRUDServiceImpl
import services.constants.QuestServiceConstants.*
import testData.TestConstants.*
import weaver.SimpleIOSuite

object QuestCRUDServiceSpec extends SimpleIOSuite with ServiceSpecBase {

  val configReader: ConfigReaderAlgebra[IO] = ConfigReader[IO]
  val mockUserDataRepository = MockUserDataRepository
  val mockLevelService = MockLevelService

  test(".getByQuestId() - when there is an existing quest details given a businessId should return the correct address details - Right(address)") {

    val existingQuestForUser = testQuest(userId1, Some(devId1), questId1)

    val mockQuestRepository = new MockQuestRepository(Map(businessId1 -> existingQuestForUser))

    for {
      appConfig <- configReader.loadAppConfig
      service = QuestCRUDService(appConfig, mockQuestRepository, mockUserDataRepository, mockLevelService)
      result <- service.getByQuestId(businessId1)
    } yield expect(result == Some(existingQuestForUser))
  }

  test(".create() - should create a quest, ") {

    val existingQuestForUser = testQuest(clientId1, Some(devId1), questId1)

    val mockQuestRepository = new MockQuestRepository(Map(clientId1 -> existingQuestForUser))

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
      service = QuestCRUDService(appConfig, mockQuestRepository, mockUserDataRepository, mockLevelService)
      result <- service.create(createQuestPartial, clientId1)
    } yield expect(result == Valid(CreateSuccess))
  }

  test(".update() - should update the details of a given quest") {

    val existingQuestForUser = testQuest(clientId1, Some(devId1), questId1)

    val mockQuestRepository = new MockQuestRepository(Map(clientId1 -> existingQuestForUser))

    val updateQuestPartial =
      UpdateQuestPartial(
        rank = Steel,
        title = "Some title",
        description = Some("description"),
        acceptanceCriteria = Some("acceptanceCriteria")
      )

    for {
      appConfig <- configReader.loadAppConfig
      service = QuestCRUDService(appConfig, mockQuestRepository, mockUserDataRepository, mockLevelService)
      result <- service.update(questId1, updateQuestPartial)
    } yield expect(result == Valid(UpdateSuccess))
  }

  test(".update() - should update the details of a given quest") {

    val existingQuestForUser = testQuest(clientId1, Some(devId1), questId1)

    val mockQuestRepository = new MockQuestRepository(Map(clientId1 -> existingQuestForUser))

    val updateQuestPartial =
      UpdateQuestPartial(
        rank = Steel,
        title = "Some title",
        description = Some("description"),
        acceptanceCriteria = Some("acceptanceCriteria")
      )

    for {
      appConfig <- configReader.loadAppConfig
      service = QuestCRUDService(appConfig, mockQuestRepository, mockUserDataRepository, mockLevelService)
      result <- service.update(questId1, updateQuestPartial)
    } yield expect(result == Valid(UpdateSuccess))
  }

  test(".updateStatus() - should update the status of a given quest, given a questId and QuestStatus") {

    val existingQuestForUser = testQuest(clientId1, Some(devId1), questId1)

    val mockQuestRepository = new MockQuestRepository(Map(clientId1 -> existingQuestForUser))

    for {
      appConfig <- configReader.loadAppConfig
      service = QuestCRUDService(appConfig, mockQuestRepository, mockUserDataRepository, mockLevelService)
      result <- service.updateStatus(questId1, Completed)
    } yield expect(result == Valid(UpdateSuccess))
  }
}
