package mocks

import cats.data.Validated.Valid
import cats.data.ValidatedNel
import cats.effect.IO
import fs2.Stream
import models.database.*
import models.languages.*
import repositories.LanguageRepositoryAlgebra

case object MockLanguageRepository extends LanguageRepositoryAlgebra[IO] {

  override def countForLanguage(language: Language): IO[Int] = ???

  override def getPaginatedLanguageData(language: Language, offset: Int, limit: Int): IO[List[LanguageData]] = ???

  override def getLanguagesForUser(username: String): IO[List[LanguageData]] = ???

  override def getAllLanguageData(): IO[List[LanguageData]] = ???

  override def getAllLanguages(devId: String): IO[List[DevLanguageData]] = ???

  override def getLanguage(devId: String, language: Language): IO[Option[LanguageData]] = ???

  override def getHiscoreLanguageData(language: Language): IO[List[LanguageData]] = ???

  override def awardLanguageXP(
    devId: String,
    username: String,
    language: Language,
    xp: BigDecimal,
    level: Int,
    nextLevel: Int,
    nextLevelXp: BigDecimal
  ): IO[ValidatedNel[DatabaseErrors, DatabaseSuccess]] = ???

}
