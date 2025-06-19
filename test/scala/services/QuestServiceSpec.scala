package services

import cats.data.Validated.Valid
import cats.effect.IO
import java.time.LocalDateTime
import mocks.*
import models.database.CreateSuccess
import services.constants.QuestServiceConstants.*
import services.QuestService
import services.QuestServiceImpl
import testData.TestConstants.*
import weaver.SimpleIOSuite

object QuestServiceSpec extends SimpleIOSuite with ServiceSpecBase {

  val mockUserDataRepository = MockUserDataRepository
  val mockSkillDataRepository = MockSkillDataRepository
  val mockLanguageRepository = MockLanguageRepository

  test(".getByQuestId() - when there is an existing quest details given a businessId should return the correct address details - Right(address)") {

    val existingQuestForUser = testQuest(userId1, Some(devId1), questId1)

    val mockQuestRepository = new MockQuestRepository(Map(businessId1 -> existingQuestForUser))

    val service = QuestService(mockQuestRepository, mockUserDataRepository, mockSkillDataRepository, mockLanguageRepository)

    for {
      result <- service.getByQuestId(businessId1)
    } yield expect(result == Some(existingQuestForUser))
  }

  test(".getByQuestId() - when there are no existing quest details given a businessId should return Left(QuestNotFound)") {

    val mockQuestRepository = new MockQuestRepository(Map())
    val service = QuestService(mockQuestRepository, mockUserDataRepository, mockSkillDataRepository, mockLanguageRepository)

    for {
      result <- service.getByQuestId(businessId1)
    } yield expect(result == None)
  }

  test(".create() - when given a Quest successfully create the address") {

    val testPartial = testQuestRequest(userId1, questId = questId1)

    val mockQuestRepository = new MockQuestRepository(Map())
    val service = QuestService(mockQuestRepository, mockUserDataRepository, mockSkillDataRepository, mockLanguageRepository)

    for {
      result <- service.create(testPartial, userId1)
    } yield expect(result == Valid(CreateSuccess))
  }
}
