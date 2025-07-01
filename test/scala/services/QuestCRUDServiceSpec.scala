package services

import cats.data.Validated.Valid
import cats.effect.IO
import configuration.*
import mocks.*
import models.database.CreateSuccess
import services.constants.QuestServiceConstants.*
import services.QuestCRUDService
import services.QuestCRUDServiceImpl
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

  test(".getByQuestId() - when there is an existing quest details given a businessId should return the correct address details - Right(address)") {

    val existingQuestForUser = testQuest(userId1, Some(devId1), questId1)

    val mockQuestRepository = new MockQuestRepository(Map(businessId1 -> existingQuestForUser))

    for {
      appConfig <- configReader.loadAppConfig
      service = QuestCRUDService(appConfig, mockQuestRepository, mockUserDataRepository, mockLevelService)
      result <- service.getByQuestId(businessId1)
    } yield expect(result == Some(existingQuestForUser))
  }
}
