package repository.quest

import cats.data.Validated.Valid
import cats.effect.IO
import cats.effect.Resource
import cats.implicits.*
import doobie.*
import doobie.implicits.*
import java.time.LocalDateTime
import models.database.*
import models.quests.CreateQuestPartial
import models.quests.QuestPartial
import models.Completed
import repositories.QuestRepositoryImpl
import repository.fragments.QuestRepoFragments.*
import shared.TransactorResource
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

  def testQuestRequest(userId: String, businessId: String): CreateQuestPartial =
    CreateQuestPartial(
      userId = userId,
      title = "",
      description = Some(""),
      status = Some(Completed)
    )

  def sharedResource: Resource[IO, QuestRepositoryImpl[IO]] = {
    val setup = for {
      transactor <- global.getOrFailR[TransactorResource]()
      questRepo = new QuestRepositoryImpl[IO](transactor.xa)
      createSchemaIfNotPresent <- initializeSchema(transactor)
    } yield questRepo

    setup
  }

  test(".findByQuestId() - should find and return the quest if user_id exists for a previously created quest") { questRepo =>

    val expectedResult =
      QuestPartial(
        userId = "USER001",
        title = "",
        description = Some(""),
        status = Some(Completed)
      )

    for {
      questOpt <- questRepo.findByQuestId("BUS001")
    } yield expect(questOpt == Some(expectedResult))
  }

  // test(".deleteQuest() - should delete the business address if quest_id exists for a previously existing quest") { questRepo =>

  //   val userId = "USER002"

  //   val expectedResult =
  //     QuestPartial(
  //       userId = "USER002",
  //       title = "",
  //       description = Some(""),
  //       status = Some(Completed)
  //     )

  //   for {
  //     firstFindResult <- questRepo.findByQuestId(questId)
  //     deleteResult <- questRepo.delete(businessId)
  //     afterDeletionFindResult <- questRepo.findByQuestId(businessId)
  //   } yield expect.all(
  //     firstFindResult == Some(expectedResult),
  //     deleteResult == Valid(DeleteSuccess),
  //     afterDeletionFindResult == None
  //   )
  // }
}
