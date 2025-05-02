// package repository

// import cats.data.Validated.Valid
// import cats.data.ValidatedNel	
// import cats.effect.kernel.Ref
// import cats.effect.IO
// import mocks.MockQuestRepository
// import models.database.*
// import models.quests.QuestPartial
// import repository.constants.QuestRepoConstants.*
// import services.RepositorySpecBase
// import testData.TestConstants.*
// import weaver.SimpleIOSuite
// import models.quests.CreateQuestPartial
// import repositories.QuestRepositoryAlgebra
// import cats.data.Validated
// import models.quests.UpdateQuestPartial


// case class MockQuestRepository(ref: Ref[IO, List[QuestPartial]]) extends QuestRepositoryAlgebra[IO] {

//   override def findByBusinessId(questId: String): IO[Option[QuestPartial]] =
//     ref.get.map(_.find(_.questId == questId))

//   override def create(request: CreateQuestPartial): IO[ValidatedNel[DatabaseErrors, DatabaseSuccess]] =
//     ref.modify(quests =>
//       (
//         QuestPartial(
//           request.userId,
//           // request.questId,
//           request.title,
//           request.description,
//           request.status
//         ) :: quests,
//         Valid(CreateSuccess)
//       )
//     )

//   override def update(questId: String, request: UpdateQuestPartial): IO[ValidatedNel[DatabaseErrors, DatabaseSuccess]] =
//     ref.modify { quests =>
//       val updatedQuests = quests.map {
//         case q if q.questId == questId =>
//           q.copy(
//             title = request.title.getOrElse(q.title),
//             description = request.description.getOrElse(q.description),
//             status = request.status.getOrElse(q.status)
//           )
//         case q => q
//       }
//       (updatedQuests, Valid(UpdateSuccess))
//     }

//   override def delete(questId: String): IO[ValidatedNel[DatabaseErrors, DatabaseSuccess]] =
//     ref.modify { quests =>
//       val (toKeep, toDelete) = quests.partition(_.questId != questId)
//       if (toDelete.nonEmpty)
//         (toKeep, Valid(DeleteSuccess))
//       else
//         (quests, Validated.invalidNel(NotFoundError))
//     }

//   override def deleteAllByUserId(userId: String): IO[ValidatedNel[DatabaseErrors, DatabaseSuccess]] =
//     ref.modify { quests =>
//       val (toKeep, toDelete) = quests.partition(_.userId != userId)
//       if (toDelete.nonEmpty)
//         (toKeep, Valid(DeleteSuccess))
//       else
//         (quests, Validated.invalidNel(NotFoundError))
//     }
// }


// object QuestRepositorySpec extends SimpleIOSuite with RepositorySpecBase {

//   def createMockRepo(initial: List[QuestPartial]): IO[MockQuestRepository] =
//   Ref.of[IO, List[QuestPartial]](initial).map(MockQuestRepository(_))


//   test(".findByQuestId() - should return an quest if questId1 exists") {
//   val existingQuestForUser: QuestPartial = testQuestPartial(userId1, questId1)

//   for {
//     mockRepo <- createMockRepo(List(existingQuestForUser))
//     result <- mockRepo.findByBusinessId(questId1)
//   } yield expect(result.contains(existingQuestForUser))
// }


//   test(".findByQuestId() - should return None if questId1 does not exist") {

//        val mockQuestRepository =
//       MockQuestRepository(
//         Map("" -> "")
//       )

//     for {
//       // mockRepo <- createMockRepo(List())
//       result <- mockRepo.findByQuestId(questId1)
//     } yield expect(result.isEmpty)
//   }

//   test(".createBusinessQuest() - when given a valid business quest should insert an quest into the postgres db") {

//     val testCreateRequest: CreateQuestPartial = testCreateQuestPartial(userId2)
//     val testQuestForUser2 = testQuestPartial(userId2)

//     for {
//       // mockRepo <- createMockRepo(List())
//       result <- mockRepo.create(testBusinessQuestRequest)
//       findInsertedQuest <- mockRepo.findByQuestId(questId2)
//     } yield expect.all(
//       result == Valid(CreateSuccess),
//       findInsertedQuest == Some(testQuestForUser2)
//     )
//   }

//   test(".delete() - when given a valid questId should delete the business quest details") {

//     val existingQuestForUser: QuestPartial = testQuestPartial(userId3, questId3)

//     for {
//       mockRepo <- createMockRepo(List(existingQuestForUser))
//       findInitiallyCreatedQuest <- mockRepo.findByQuestId(questId3)
//       result <- mockRepo.delete(questId3)
//       findInsertedQuest <- mockRepo.findByQuestId(questId3)
//     } yield expect.all(
//       findInitiallyCreatedQuest == Some(existingQuestForUser),
//       result == Valid(DeleteSuccess),
//       findInsertedQuest == None
//     )
//   }
// }
