package mocks

import cats.data.Validated.Valid
import cats.data.ValidatedNel
import cats.effect.IO
import fs2.Stream
import models.RewardStatus
import models.database.*
import models.rewards.*
import repositories.RewardRepositoryAlgebra

case class MockRewardRepository(
  existingReward: Map[String, RewardData] = Map.empty
) extends RewardRepositoryAlgebra[IO] {

  override def getRewardData(questId: String): IO[Option[RewardData]] = ???

  override def streamRewardByQuest(questId: String): Stream[IO, RewardData] = ???

  override def createCompletionReward(clientId: String, request: CreateCompletionReward): IO[ValidatedNel[DatabaseErrors, DatabaseSuccess]] = ???

  override def createTimeReward(clientId: String, request: CreateTimeReward): IO[ValidatedNel[DatabaseErrors, DatabaseSuccess]] = ???

  override def update(questId: String, updateRewardData: UpdateRewardData): IO[ValidatedNel[DatabaseErrors, DatabaseSuccess]] = ???

  override def updateWithDevId(questId: String, devId: String): IO[ValidatedNel[DatabaseErrors, DatabaseSuccess]] = ???

  override def delete(questId: String): IO[ValidatedNel[DatabaseErrors, DatabaseSuccess]] = ???

}
