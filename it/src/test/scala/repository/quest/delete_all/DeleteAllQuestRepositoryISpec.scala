// package repository.business

// import cats.data.Validated.Valid
// import cats.effect.IO
// import cats.effect.Resource
// import cats.implicits.*
// import doobie.*
// import doobie.implicits.*
// import java.time.LocalDateTime
// import java.time.LocalTime
// import models.*
// import models.database.*
// import repositories.QuestRepositoryImpl
// import shared.TransactorResource
// import testData.BusinessTestConstants.*
// import testData.TestConstants.*
// import weaver.GlobalRead
// import weaver.IOSuite
// import weaver.ResourceTag

// class DeleteAllQuestRepositoryISpec(global: GlobalRead) extends IOSuite {

//   type Res = QuestRepositoryImpl[IO]

//   private def initializeSchema(transactor: TransactorResource): Resource[IO, Unit] =
//     Resource.eval(
//       createQuestTable.update.run.transact(transactor.xa).void *>
//         resetQuestTable.update.run.transact(transactor.xa).void *>
//         insertQuestData.update.run.transact(transactor.xa).void
//     )

//   def sharedResource: Resource[IO, QuestRepositoryImpl[IO]] = {
//     val setup = for {
//       transactor <- global.getOrFailR[TransactorResource]()
//       questRepo = new QuestRepositoryImpl[IO](transactor.xa)
//       createSchemaIfNotPresent <- initializeSchema(transactor)
//     } yield questRepo

//     setup
//   }

//   test(".deleteAll() - should delete all of the business availability data for a given businessId") { questRepo =>

//     val expectedUpdateResult = DeleteSuccess

//     for {
//       deleteResult <- questRepo.deleteAll(businessId1)
//     } yield expect.all(
//       deleteResult == Valid(expectedUpdateResult)
//     )
//   }
// }
