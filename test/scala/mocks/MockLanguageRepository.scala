package mocks

import cats.data.Validated.Valid
import cats.data.ValidatedNel
import cats.effect.IO
import fs2.Stream
import models.database.*
import models.database.CreateSuccess
import models.database.DatabaseErrors
import models.database.DatabaseSuccess
import models.languages.Language
import models.languages.LanguageData
import repositories.LanguageRepositoryAlgebra

case object MockLanguageRepository extends LanguageRepositoryAlgebra[IO] {

  override def getAllLanguages(devId: String): IO[List[LanguageData]] = ???
  
  override def getLanguage(devId: String, language: Language): IO[Option[LanguageData]] = ???

  override def getHiscoreLanguageData(language: Language): IO[List[LanguageData]] = ???

  override def awardLanguageXP(devId: String, username: String, language: String, xp: BigDecimal): IO[ValidatedNel[DatabaseErrors, DatabaseSuccess]] = ???

}
