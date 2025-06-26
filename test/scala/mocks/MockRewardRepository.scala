package mocks

import cats.data.Validated.Valid
import cats.data.ValidatedNel
import cats.effect.IO
import fs2.Stream
import models.RewardStatus
import models.database.*
import models.database.CreateSuccess
import models.database.DatabaseErrors
import models.database.DatabaseSuccess
import models.rewards.*
import repositories.RewardRepositoryAlgebra

case class MockRewardRepository(
  existingReward: Map[String, RewardData] = Map.empty
) extends RewardRepositoryAlgebra[IO] {

  def getRewardData(questId: String): IO[Option[RewardData]] = ???

  def streamRewardByQuest(questId: String): Stream[IO, RewardData] = ???

  def create(clientId: String, request: CreateReward): IO[ValidatedNel[DatabaseErrors, DatabaseSuccess]] = ???

  def update(questId: String, updateRewardData: UpdateRewardData): IO[ValidatedNel[DatabaseErrors, DatabaseSuccess]] = ???

  def updateWithDevId(questId: String, devId: String): IO[ValidatedNel[DatabaseErrors, DatabaseSuccess]] = ???

  def delete(questId: String): IO[ValidatedNel[DatabaseErrors, DatabaseSuccess]] = ???

}
