package repository

import cats.data.Validated.Valid
import cats.effect.IO
import cats.effect.Resource
import cats.implicits.*
import doobie.*
import doobie.implicits.*
import java.time.LocalDateTime
import models.database.*
import models.database.DeleteSuccess
import models.database.UpdateSuccess
import models.languages.*
import models.quests.CreateQuestPartial
import models.quests.QuestPartial
import models.quests.UpdateQuestPartial
import models.Completed
import models.Demon
import models.InProgress
import repositories.QuestRepositoryImpl
import repository.fragments.QuestRepoFragments.*
import repository.RepositoryISpecBase
import routes.Routes.estimateRoutes
import scala.collection.immutable.ArraySeq
import shared.TransactorResource
import testData.ITestConstants.*
import weaver.GlobalRead
import weaver.IOSuite
import weaver.ResourceTag

class QuestRepositoryISpec(global: GlobalRead) extends IOSuite with RepositoryISpecBase {
  type Res = QuestRepositoryImpl[IO]

  private def initializeSchema(transactor: TransactorResource): Resource[IO, Unit] =
    Resource.eval(
      createQuestTable.update.run.transact(transactor.xa).void *>
        resetQuestTable.update.run.transact(transactor.xa).void *>
        insertQuestData.update.run.transact(transactor.xa).void
    )

  def testQuestRequest(clientId: String, businessId: String): CreateQuestPartial =
    CreateQuestPartial(
      rank = Demon,
      title = "Implement User Authentication",
      description = Some("Set up Auth0 integration and secure routes using JWT tokens."),
      acceptanceCriteria = "Set up Auth0 integration and secure routes using JWT tokens.",
      tags = Seq(Python, Scala, TypeScript)
    )

  def sharedResource: Resource[IO, QuestRepositoryImpl[IO]] = {
    val setup = for {
      transactor <- global.getOrFailR[TransactorResource]()
      questRepo = new QuestRepositoryImpl[IO](transactor.xa)
      createSchemaIfNotPresent <- initializeSchema(transactor)
    } yield questRepo

    setup
  }

  test(".findAllByUserId() - should find and return the quest if user_id exists for a previously created quest") { questRepo =>

    val expectedResult =
      QuestPartial(
        clientId = "USER001",
        questId = "QUEST001",
        devId = Some("DEV001"),
        rank = Demon,
        title = "Implement User Authentication",
        description = Some("Set up Auth0 integration and secure routes using JWT tokens."),
        acceptanceCriteria = Some("Some acceptance criteria"),
        status = Some(InProgress),
        tags = ArraySeq("Python", "Scala", "Typescript"),
        estimated = true
      )

    for {
      quests <- questRepo.findAllByUserId("USER001")
    } yield expect(quests == List(expectedResult))
  }

  test(".findByQuestId() - should find and return the quest if quest_id exists for a previously created quest") { questRepo =>

    val expectedResult =
      QuestPartial(
        clientId = "USER001",
        questId = "QUEST001",
        devId = Some("DEV001"),
        rank = Demon,
        title = "Implement User Authentication",
        description = Some("Set up Auth0 integration and secure routes using JWT tokens."),
        acceptanceCriteria = Some("Some acceptance criteria"),
        status = Some(InProgress),
        tags = ArraySeq("Python", "Scala", "Typescript"),
        estimated = true
      )

    for {
      questOpt <- questRepo.findByQuestId("QUEST001")
    } yield expect(questOpt == Some(expectedResult))
  }

  test(".update() - for a given quest_id should update the quest details if previously created quest exists") { questRepo =>

    val updateRequest =
      UpdateQuestPartial(
        rank = Demon,
        title = "Implement User Authentication",
        description = Some("Set up Auth0 integration and secure routes using JWT tokens."),
        acceptanceCriteria = Some("Set up Auth0 integration and secure routes using JWT tokens.")
      )

    for {
      questOpt <- questRepo.update("QUEST002", updateRequest)
    } yield expect(questOpt == Valid(UpdateSuccess))
  }

  test(".deleteQuest() - should delete the QUEST003 quest if quest_id exists for the previously existing quest") { questRepo =>

    val questId = "QUEST003"

    val expectedResult =
      QuestPartial(
        clientId = "USER003",
        questId = questId,
        devId = Some("DEV003"),
        rank = Demon,
        title = "Refactor API Layer",
        description = Some("Migrate from custom HTTP clients to use http4s and apply middleware."),
        acceptanceCriteria = Some("Some acceptance criteria"),
        status = Some(InProgress),
        tags = ArraySeq("Python", "Scala", "Typescript"),
        estimated = true
      )

    for {
      firstFindResult <- questRepo.findByQuestId(questId)
      deleteResult <- questRepo.delete(questId)
      afterDeletionFindResult <- questRepo.findByQuestId(questId)
    } yield expect.all(
      firstFindResult == Some(expectedResult),
      deleteResult == Valid(DeleteSuccess),
      afterDeletionFindResult == None
    )
  }
}
