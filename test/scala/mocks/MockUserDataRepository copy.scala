package mocks

import cats.data.Validated.Valid
import cats.data.ValidatedNel
import cats.effect.IO
import fs2.Stream
import models.database.*
import models.work_time.HoursOfWork
import repositories.HoursWorkedRepositoryAlgebra

case object MockHoursWorkedRepository extends HoursWorkedRepositoryAlgebra[IO] {

  override def getHoursOfWork(questId: String): IO[Option[HoursOfWork]] = ???

  override def upsertHoursOfWork(clientId: String, questId: String, request: HoursOfWork): IO[ValidatedNel[DatabaseErrors, DatabaseSuccess]] = ???

}
