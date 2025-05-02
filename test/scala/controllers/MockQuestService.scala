package controllers

import cats.data.Validated.Valid
import cats.data.ValidatedNel
import cats.effect.IO
import models.database.*
import models.quests.CreateQuestPartial
import models.quests.QuestPartial
import models.quests.UpdateQuestPartial
import services.QuestServiceAlgebra

class MockQuestService(userQuestData: Map[String, QuestPartial]) extends QuestServiceAlgebra[IO] {

  override def getByQuestId(businessId: String): IO[Option[QuestPartial]] =
    userQuestData.get(businessId) match {
      case Some(address) => IO.pure(Some(address))
      case None => IO.pure(None)
    }

  override def create(request: CreateQuestPartial): IO[ValidatedNel[DatabaseErrors, DatabaseSuccess]] =
    IO.pure(Valid(CreateSuccess))

  override def update(businessId: String, request: UpdateQuestPartial): IO[ValidatedNel[DatabaseErrors, DatabaseSuccess]] =
    IO.pure(Valid(UpdateSuccess))

  override def delete(businessId: String): IO[ValidatedNel[DatabaseErrors, DatabaseSuccess]] =
    IO.pure(Valid(DeleteSuccess))
}
