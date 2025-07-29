package services

import cats.data.Validated
import cats.data.Validated.Invalid
import cats.data.Validated.Valid
import cats.data.ValidatedNel
import cats.effect.Concurrent
import cats.implicits.*
import cats.syntax.all.*
import cats.Monad
import cats.NonEmptyParallel
import fs2.Stream
import java.util.UUID
import models.database.*
import models.database.DatabaseErrors
import models.database.DatabaseSuccess
import models.languages.DevLanguageData
import models.profile.ProfileData
import models.profile.ProfileLanguageData
import models.profile.ProfileSkillData
import models.skills.DevSkillData
import models.skills.Skill
import models.skills.SkillData
import models.users.*
import models.UserType
import org.typelevel.log4cats.Logger
import repositories.LanguageRepositoryAlgebra
import repositories.SkillDataRepositoryAlgebra

trait ProfileServiceAlgebra[F[_]] {

  def getSkillAndLanguageData(devId: String): F[List[ProfileData]]
}

class ProfileServiceImpl[F[_] : Concurrent : Monad : Logger](
  skillRepo: SkillDataRepositoryAlgebra[F],
  languageRepo: LanguageRepositoryAlgebra[F]
) extends ProfileServiceAlgebra[F] {

  override def getSkillAndLanguageData(devId: String): F[List[ProfileData]] =
    for {
      userSkills: List[DevSkillData] <- skillRepo.getAllSkills(devId)
      userLanguages: List[DevLanguageData] <- languageRepo.getAllLanguages(devId)

      allUsernames = (userSkills.map(_.username) ++ userLanguages.map(_.username)).distinct
      username = allUsernames match {
        case single :: Nil => Some(single)
        case _ => None
      }

      profileSkillData = userSkills.map(skill => ProfileSkillData(skill.skill, skill.level, skill.xp, skill.nextLevel, skill.nextLevelXp))
      profileLanguageData = userLanguages.map(language => ProfileLanguageData(language.language, language.level, language.xp, language.nextLevel, language.nextLevelXp))

    } yield List(
      ProfileData(
        devId = devId,
        username = username,
        skillData = profileSkillData,
        languageData = profileLanguageData
      )
    )

}

object ProfileService {

  def apply[F[_] : Concurrent : NonEmptyParallel : Logger](
    skillRepo: SkillDataRepositoryAlgebra[F],
    languageRepo: LanguageRepositoryAlgebra[F]
  ): ProfileServiceAlgebra[F] =
    new ProfileServiceImpl[F](skillRepo, languageRepo)
}
