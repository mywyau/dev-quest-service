package repository

import cats.data.Validated.Valid
import cats.effect.IO
import cats.effect.Resource
import cats.implicits.*
import doobie.*
import doobie.implicits.*
import models.NotPaid
import models.database.*
import models.rewards.*
import repositories.RewardRepositoryImpl
import repository.RepositoryISpecBase
import repository.fragments.RewardRepoFragments.*
import shared.TransactorResource
import testData.ITestConstants.*
import weaver.GlobalRead
import weaver.IOSuite
import weaver.ResourceTag

import java.time.LocalDateTime

class RewardRepositoryISpec(global: GlobalRead) extends IOSuite with RepositoryISpecBase {
  type Res = RewardRepositoryImpl[IO]

  private def initializeSchema(transactor: TransactorResource): Resource[IO, Unit] =
    Resource.eval(
      createRewardTable.update.run.transact(transactor.xa).void *>
        resetRewardTable.update.run.transact(transactor.xa).void *>
        insertRewardData.update.run.transact(transactor.xa).void
    )

  def sharedResource: Resource[IO, RewardRepositoryImpl[IO]] = {
    val setup = for {
      transactor <- global.getOrFailR[TransactorResource]()
      rewardRepo = new RewardRepositoryImpl[IO](transactor.xa)
      createSchemaIfNotPresent <- initializeSchema(transactor)
    } yield rewardRepo

    setup
  }

  test(".findReward() - should find and return the reward type if reward_id exists for a previously created reward") { rewardRepo =>

    val expectedResult =
      RewardData(
        questId = "QUEST001",
        clientId = "CLIENT001",
        devId = Some("DEV001"),
        timeRewardValue = 10.5,
        completionRewardValue = 100.00,
        paid = NotPaid
      )

    for {
      rewards <- rewardRepo.getRewardData("QUEST001")
    } yield expect(rewards == Option(expectedResult))
  }

  test(".update() - should find and update the reward's type if reward_id exists for a previously created reward returning UpdateSuccess response") { rewardRepo =>

    val originalReward =
      RewardData(
        questId = "QUEST002",
        clientId = "CLIENT002",
        devId = Some("DEV002"),
        timeRewardValue = 20.0,
        completionRewardValue = 200.0,
        paid = NotPaid
      )

    val expectedResult =
      originalReward.copy(
        timeRewardValue = 100.00,
        completionRewardValue = 2000
        )

    for {
      originalData <- rewardRepo.getRewardData("QUEST002")
      result <- rewardRepo.update(
        "QUEST002",
        UpdateRewardData(
          timeRewardValue = 100.00,
          completionRewardValue = 2000
        )
      )
      updatedReward <- rewardRepo.getRewardData("QUEST002")
    } yield expect.all(
      originalData == Some(originalReward),
      result == Valid(UpdateSuccess),
      updatedReward == Some(expectedResult)
    )
  }

  // test(".deleteReward() - should delete the USER003 reward if reward_id exists for the previously existing reward") { rewardRepo =>

  //   val rewardId = "USER003"

  //   val expectedResult =
  //     Reward(
  //       rewardId = "USER003",
  //       email = "sam_smith@gmail.com",
  //       rewardname = "goku",
  //       firstName = Some("Sam"),
  //       lastName = Some("Smith"),
  //       rewardType = Some(Dev)
  //     )

  //   for {
  //     firstFindResult <- rewardRepo.findReward(rewardId)
  //     deleteResult <- rewardRepo.deleteReward(rewardId)
  //     afterDeletionFindResult <- rewardRepo.findReward(rewardId)
  //   } yield expect.all(
  //     firstFindResult == Some(expectedResult),
  //     deleteResult == Valid(DeleteSuccess),
  //     afterDeletionFindResult == None
  //   )
  // }

}
