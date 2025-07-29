package services

import cats.data.Validated
import cats.data.Validated.Invalid
import cats.data.Validated.Valid
import cats.data.ValidatedNel
import cats.effect.IO
import cats.syntax.all.*
import configuration.AppConfig
import configuration.ConfigReader
import configuration.ConfigReaderAlgebra
import fs2.Stream
import java.time.Instant
import models.*
import models.database.*
import models.quests.*
import models.rewards.*
import models.Bronze
import models.Open
import org.typelevel.log4cats.slf4j.Slf4jLogger
import repositories.*
import weaver.SimpleIOSuite

object QuestStreamingServiceSpec extends SimpleIOSuite with ServiceSpecBase {

  val fixedInstant = Instant.parse("2025-07-27T12:00:00Z")

  val sampleQuest =
    QuestPartial(
      questId = "quest123",
      clientId = "client123",
      devId = Some("dev123"),
      title = "Quest Title",
      description = Some("Do something"),
      acceptanceCriteria = Some("acceptance criteria"),
      rank = Bronze,
      status = Some(Open),
      tags = List("Scala"),
      estimationCloseAt = Some(fixedInstant),
      estimated = true
    )

  val reward =
    RewardData(
      questId = "quest123",
      clientId = "client123",
      devId = Some("dev123"),
      timeRewardValue = 100.00,
      completionRewardValue = 1000.00,
      paid = NotPaid
    )

  val questRepo = new QuestRepositoryAlgebra[IO] {

    override def setEstimationCloseAt(questId: String, closeAt: Instant): IO[ValidatedNel[DatabaseErrors, DatabaseSuccess]] = ???

    override def findQuestsWithExpiredEstimation(now: Instant): IO[ValidatedNel[DatabaseErrors, ReadSuccess[List[QuestPartial]]]] = ???

    override def countNotEstimatedAndOpenQuests(): IO[Int] = ???

    override def setFinalRank(questId: String, rank: Rank): IO[ValidatedNel[DatabaseErrors, DatabaseSuccess]] = ???

    override def streamByQuestStatus(clientId: String, status: QuestStatus, limit: Int, offset: Int): Stream[IO, QuestPartial] =
      Stream.emit(sampleQuest)

    override def streamByQuestStatusDev(devId: String, status: QuestStatus, limit: Int, offset: Int): Stream[IO, QuestPartial] =
      Stream.emit(sampleQuest.copy(devId = Some(devId)))

    override def streamAll(limit: Int, offset: Int): Stream[IO, QuestPartial] =
      Stream.emits(List(sampleQuest, sampleQuest.copy(questId = "q-2")))

    override def streamByUserId(clientId: String, limit: Int, offset: Int): Stream[IO, QuestPartial] =
      Stream.emit(sampleQuest)

    override def findByQuestId(id: String): IO[Option[QuestPartial]] = IO.pure(None)
    override def findAllByUserId(id: String): IO[List[QuestPartial]] = IO.pure(Nil)
    override def create(quest: CreateQuest): IO[ValidatedNel[DatabaseErrors, DatabaseSuccess]] = ???
    override def update(id: String, request: UpdateQuestPartial): IO[ValidatedNel[DatabaseErrors, DatabaseSuccess]] = ???
    override def updateStatus(id: String, status: QuestStatus): IO[ValidatedNel[DatabaseErrors, DatabaseSuccess]] = ???
    override def delete(id: String): IO[ValidatedNel[DatabaseErrors, DatabaseSuccess]] = ???
    override def countActiveQuests(devId: String): IO[Int] = IO.pure(0)
    override def acceptQuest(id: String, devId: String): IO[ValidatedNel[DatabaseErrors, DatabaseSuccess]] = ???

    override def deleteAllByUserId(clientId: String): IO[ValidatedNel[models.database.DatabaseErrors, models.database.DatabaseSuccess]] = ???
    override def markPaid(questId: String): cats.effect.IO[Unit] = ???
    override def validateOwnership(questId: String, clientId: String): cats.effect.IO[Unit] = ???
  }

  val rewardRepo = new RewardRepositoryAlgebra[IO] {
    override def streamRewardByQuest(questId: String): Stream[IO, RewardData] = Stream.emit(reward)
    override def getRewardData(questId: String): IO[Option[RewardData]] = ???
    override def create(clientId: String, request: CreateReward): IO[ValidatedNel[DatabaseErrors, DatabaseSuccess]] = ???
    override def update(questId: String, updateRewardData: UpdateRewardData): IO[ValidatedNel[DatabaseErrors, DatabaseSuccess]] = ???
    override def updateWithDevId(questId: String, devId: String): IO[ValidatedNel[DatabaseErrors, DatabaseSuccess]] = ???
    override def delete(questId: String): IO[ValidatedNel[DatabaseErrors, DatabaseSuccess]] = ???
  }

  val configReader: ConfigReaderAlgebra[IO] = ConfigReader[IO]

  test(".streamClient() - should return quest with expected ID") {

    for {
      appConfig <- configReader.loadAppConfig
      service = QuestStreamingServiceImpl[IO](appConfig, questRepo, rewardRepo)
      results <- service.streamClient("client123", Open, 10, 0).compile.toList
    } yield expect(results.exists(_.questId == "quest123"))
  }

  test(".streamAll() - should return 2 quests") {

    for {
      appConfig <- configReader.loadAppConfig
      service = QuestStreamingServiceImpl[IO](appConfig, questRepo, rewardRepo)
      results <- service.streamAll(10, 0).compile.toList
    } yield expect(results.length == 2)
  }

  test(".streamAllWithRewards() - should return quests with correct reward") {

    for {
      appConfig <- configReader.loadAppConfig
      service = QuestStreamingServiceImpl[IO](appConfig, questRepo, rewardRepo)
      results <- service.streamAllWithRewards(10, 0).compile.toList
    } yield expect(results.forall(_.reward.contains(reward)))
  }

}
