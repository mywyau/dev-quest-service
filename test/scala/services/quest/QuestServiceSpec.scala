package services.quest

import cats.data.Validated.Valid
import cats.effect.IO
import java.time.LocalDateTime
import models.database.CreateSuccess
import services.QuestService
import services.QuestServiceImpl
import testData.TestConstants.*
import weaver.SimpleIOSuite
import mocks.MockQuestRepository
import services.constants.QuestServiceConstants.*

object QuestServiceSpec extends SimpleIOSuite {

  test(".getByQuestId() - when there is an existing quest details given a businessId should return the correct address details - Right(address)") {

    val existingQuestForUser = testQuest(userId1, businessId1)

    val mockQuestRepository = new MockQuestRepository(Map(businessId1 -> existingQuestForUser))
    val service = new QuestServiceImpl[IO](mockQuestRepository)

    for {
      result <- service.getByQuestId(businessId1)
    } yield expect(result == Some(existingQuestForUser))
  }

  test(".getByQuestId() - when there are no existing quest details given a businessId should return Left(QuestNotFound)") {

    val mockQuestRepository = new MockQuestRepository(Map())
    val service = new QuestServiceImpl[IO](mockQuestRepository)

    for {
      result <- service.getByQuestId(businessId1)
    } yield expect(result == None)
  }

  test(".create() - when given a Quest successfully create the address") {

    val testQuestRequest = testQuestRequest(userId1, businessId1)

    val mockQuestRepository = new MockQuestRepository(Map())
    val service = QuestService(mockQuestRepository)

    for {
      result <- service.create(testQuestRequest)
    } yield expect(result == Valid(CreateSuccess))
  }
}
