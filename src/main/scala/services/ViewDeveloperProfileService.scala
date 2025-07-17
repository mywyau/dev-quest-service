package services

import cats.Monad
import cats.NonEmptyParallel
import cats.data.Validated
import cats.data.Validated.Invalid
import cats.data.Validated.Valid
import cats.data.ValidatedNel
import cats.effect.Concurrent
import cats.implicits.*
import cats.syntax.all.*
import models.UserType
import models.database.*
import models.languages.LanguageData
import models.profile.DevProfileData
import models.profile.ProfileLanguageData
import models.profile.ProfileSkillData
import models.skills.SkillData
import models.users.*
import org.typelevel.log4cats.Logger
import repositories.LanguageRepositoryAlgebra
import repositories.SkillDataRepositoryAlgebra
import repositories.UserDataRepositoryAlgebra
import repositories.ViewDevUserDataRepositoryAlgebra

trait ViewDeveloperProfileServiceAlgebra[F[_]] {

  def getDevProfileData(username: String): F[Option[DevProfileData]]
}

import cats.data.OptionT
import cats.effect.Concurrent
import cats.syntax.all.*
import org.typelevel.log4cats.Logger
import repositories.*

class ViewDeveloperProfileServiceImpl[F[_] : Concurrent : Logger](
  viewDevUserDataRepo: ViewDevUserDataRepositoryAlgebra[F],
  skillRepo: SkillDataRepositoryAlgebra[F],
  languageRepo: LanguageRepositoryAlgebra[F]
) extends ViewDeveloperProfileServiceAlgebra[F] {

  override def getDevProfileData(username: String): F[Option[DevProfileData]] = {
    val result = for {
      userDetails: DevUserData <- OptionT(viewDevUserDataRepo.findDevUser(username))
      devSkills: List[SkillData] <- OptionT.liftF(skillRepo.getSkillsForUser(username))
      devLanguages: List[LanguageData] <- OptionT.liftF(languageRepo.getLanguagesForUser(username))

      profileSkills = devSkills.map(s => ProfileSkillData(s.skill, s.level, s.xp))
      profileLangs = devLanguages.map(l => ProfileLanguageData(l.language, l.level, l.xp))

    } yield DevProfileData(
      devId = userDetails.userId,
      username = userDetails.username,
      email = userDetails.email,
      mobile = userDetails.mobile,
      skillData = profileSkills,
      languageData = profileLangs
    )

    result.value
  }
}

object ViewDeveloperProfileService {

  def apply[F[_] : Concurrent : NonEmptyParallel : Logger](
    viewDevUserDataRepo: ViewDevUserDataRepositoryAlgebra[F],
    skillRepo: SkillDataRepositoryAlgebra[F],
    languageRepo: LanguageRepositoryAlgebra[F]
  ): ViewDeveloperProfileServiceAlgebra[F] =
    new ViewDeveloperProfileServiceImpl[F](viewDevUserDataRepo, skillRepo, languageRepo)
}
