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
import models.database.*
import models.languages.LanguageData
import models.skills.SkillData
import models.users.*
import models.view_dev_profile.DevProfileData
import models.view_dev_profile.ViewProfileLanguageData
import models.view_dev_profile.ViewProfileSkillData
import models.UserType
import org.typelevel.log4cats.Logger
import repositories.DevLanguageRepositoryAlgebra
import repositories.DevSkillRepositoryAlgebra
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
  skillRepo: DevSkillRepositoryAlgebra[F],
  languageRepo: DevLanguageRepositoryAlgebra[F]
) extends ViewDeveloperProfileServiceAlgebra[F] {

  override def getDevProfileData(username: String): F[Option[DevProfileData]] = {
    val result = for {

      userDetails: DevUserData <- OptionT(viewDevUserDataRepo.findDevUser(username))
      devSkills: List[SkillData] <- OptionT.liftF(skillRepo.getSkillsForUser(username))
      devLanguages: List[LanguageData] <- OptionT.liftF(languageRepo.getLanguagesForUser(username))

      profileSkills = devSkills.map(s => ViewProfileSkillData(s.skill, s.level))
      profileLangs = devLanguages.map(l => ViewProfileLanguageData(l.language, l.level))

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
    skillRepo: DevSkillRepositoryAlgebra[F],
    languageRepo: DevLanguageRepositoryAlgebra[F]
  ): ViewDeveloperProfileServiceAlgebra[F] =
    new ViewDeveloperProfileServiceImpl[F](viewDevUserDataRepo, skillRepo, languageRepo)
}
