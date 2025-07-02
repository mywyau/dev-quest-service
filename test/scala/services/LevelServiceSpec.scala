package services

import cats.data.Validated.Valid
import cats.data.ValidatedNel
import cats.effect.IO
import cats.syntax.all.*
import models.database.*
import models.languages.*
import models.skills.*
import org.typelevel.log4cats.slf4j.Slf4jLogger
import repositories.*
import services.*
import weaver.SimpleIOSuite

object LevelServiceSpec extends SimpleIOSuite with ServiceSpecBase {

  val logger = Slf4jLogger.getLogger[IO]

  test(".awardSkillXpWithLevel() - returns correct XP and level") {

    val mockSkillRepo = new SkillDataRepositoryAlgebra[IO] {

      override def getAllSkillData(): IO[List[SkillData]] = ???

      override def getAllSkills(devId: String): IO[List[SkillData]] = IO.pure(Nil)

      override def getHiscoreSkillData(skill: Skill): IO[List[SkillData]] = IO.pure(Nil)

      override def getSkill(
        devId: String,
        skill: Skill
      ): IO[Option[SkillData]] =
        IO.pure(Some(SkillData(devId, "mike", skill, level = 1, xp = 2000)))

      override def awardSkillXP(
        devId: String,
        username: String,
        skill: Skill,
        xp: BigDecimal,
        level: Int
      ): IO[ValidatedNel[DatabaseErrors, DatabaseSuccess]] =
        IO.pure(Valid(UpdateSuccess))
    }

    val mockLangRepo = new LanguageRepositoryAlgebra[IO] {

      override def getAllLanguageData(): IO[List[LanguageData]] = ???
      override def getAllLanguages(devId: String): IO[List[LanguageData]] = IO.pure(Nil)
      override def getLanguage(devId: String, language: Language): IO[Option[LanguageData]] = IO.pure(None)
      override def getHiscoreLanguageData(language: Language): IO[List[LanguageData]] = IO.pure(Nil)
      override def awardLanguageXP(
        devId: String,
        username: String,
        language: Language,
        xp: BigDecimal,
        level: Int
      ): IO[ValidatedNel[DatabaseErrors, DatabaseSuccess]] =
        IO.pure(Valid(UpdateSuccess))
    }

    val service = new LevelServiceImpl[IO](mockSkillRepo, mockLangRepo)

    for {
      result <- service.awardSkillXpWithLevel("dev123", "mike", Questing, 1000)
    } yield expect(result == Valid(UpdateSuccess))
  }

  test(".calculateLevel() - returns the correct level: '4', for 1000 xp") {

    val mockSkillRepo = new SkillDataRepositoryAlgebra[IO] {

      override def getAllSkillData(): IO[List[SkillData]] = ???

      override def getAllSkills(devId: String): IO[List[SkillData]] = IO.pure(Nil)

      override def getHiscoreSkillData(skill: Skill): IO[List[SkillData]] = IO.pure(Nil)

      override def getSkill(
        devId: String,
        skill: Skill
      ): IO[Option[SkillData]] =
        IO.pure(Some(SkillData(devId, "mike", skill, level = 1, xp = 2000)))

      override def awardSkillXP(
        devId: String,
        username: String,
        skill: Skill,
        xp: BigDecimal,
        level: Int
      ): IO[ValidatedNel[DatabaseErrors, DatabaseSuccess]] =
        IO.pure(Valid(UpdateSuccess))
    }

    val mockLangRepo = new LanguageRepositoryAlgebra[IO] {

      override def getAllLanguageData(): IO[List[LanguageData]] = ???
      override def getAllLanguages(devId: String): IO[List[LanguageData]] = IO.pure(Nil)
      override def getLanguage(devId: String, language: Language): IO[Option[LanguageData]] = IO.pure(None)
      override def getHiscoreLanguageData(language: Language): IO[List[LanguageData]] = IO.pure(Nil)
      override def awardLanguageXP(
        devId: String,
        username: String,
        language: Language,
        xp: BigDecimal,
        level: Int
      ): IO[ValidatedNel[DatabaseErrors, DatabaseSuccess]] =
        IO.pure(Valid(UpdateSuccess))
    }

    val service = new LevelServiceImpl[IO](mockSkillRepo, mockLangRepo)
    val resultantLevel = service.calculateLevel(1000.0)

    IO(expect(resultantLevel == 8))
  }

  test(".calculateLevel() - returns the correct level: '97', for 10,000,000 xp") {

    val mockSkillRepo = new SkillDataRepositoryAlgebra[IO] {

      override def getAllSkillData(): IO[List[SkillData]] = ???

      override def getAllSkills(devId: String): IO[List[SkillData]] = IO.pure(Nil)

      override def getHiscoreSkillData(skill: Skill): IO[List[SkillData]] = IO.pure(Nil)

      override def getSkill(
        devId: String,
        skill: Skill
      ): IO[Option[SkillData]] =
        IO.pure(Some(SkillData(devId, "mike", skill, level = 1, xp = 2000)))

      override def awardSkillXP(
        devId: String,
        username: String,
        skill: Skill,
        xp: BigDecimal,
        level: Int
      ): IO[ValidatedNel[DatabaseErrors, DatabaseSuccess]] =
        IO.pure(Valid(UpdateSuccess))
    }

    val mockLangRepo = new LanguageRepositoryAlgebra[IO] {

      override def getAllLanguageData(): IO[List[LanguageData]] = ???
      override def getAllLanguages(devId: String): IO[List[LanguageData]] = IO.pure(Nil)
      override def getLanguage(devId: String, language: Language): IO[Option[LanguageData]] = IO.pure(None)
      override def getHiscoreLanguageData(language: Language): IO[List[LanguageData]] = IO.pure(Nil)
      override def awardLanguageXP(
        devId: String,
        username: String,
        language: Language,
        xp: BigDecimal,
        level: Int
      ): IO[ValidatedNel[DatabaseErrors, DatabaseSuccess]] =
        IO.pure(Valid(UpdateSuccess))
    }

    val service = new LevelServiceImpl[IO](mockSkillRepo, mockLangRepo)
    val resultantLevel = service.calculateLevel(10000000.0)

    IO(expect(resultantLevel == 97))
  }

  test(".calculateLevel() - returns the correct level: '101', for 15,000,000 xp") {

    val mockSkillRepo = new SkillDataRepositoryAlgebra[IO] {

      override def getAllSkillData(): IO[List[SkillData]] = ???

      override def getAllSkills(devId: String): IO[List[SkillData]] = IO.pure(Nil)

      override def getHiscoreSkillData(skill: Skill): IO[List[SkillData]] = IO.pure(Nil)

      override def getSkill(
        devId: String,
        skill: Skill
      ): IO[Option[SkillData]] =
        IO.pure(Some(SkillData(devId, "mike", skill, level = 1, xp = 2000)))

      override def awardSkillXP(
        devId: String,
        username: String,
        skill: Skill,
        xp: BigDecimal,
        level: Int
      ): IO[ValidatedNel[DatabaseErrors, DatabaseSuccess]] =
        IO.pure(Valid(UpdateSuccess))
    }

    val mockLangRepo = new LanguageRepositoryAlgebra[IO] {

      override def getAllLanguageData(): IO[List[LanguageData]] = ???
      override def getAllLanguages(devId: String): IO[List[LanguageData]] = IO.pure(Nil)
      override def getLanguage(devId: String, language: Language): IO[Option[LanguageData]] = IO.pure(None)
      override def getHiscoreLanguageData(language: Language): IO[List[LanguageData]] = IO.pure(Nil)
      override def awardLanguageXP(
        devId: String,
        username: String,
        language: Language,
        xp: BigDecimal,
        level: Int
      ): IO[ValidatedNel[DatabaseErrors, DatabaseSuccess]] =
        IO.pure(Valid(UpdateSuccess))
    }

    val service = new LevelServiceImpl[IO](mockSkillRepo, mockLangRepo)
    val resultantLevel = service.calculateLevel(15000000.0)

    IO(expect(resultantLevel == 101))
  }

  test(".awardSkillXpWithLevel() - returns correct XP and level") {

    val mockSkillRepo = new SkillDataRepositoryAlgebra[IO] {

      override def getAllSkillData(): IO[List[SkillData]] = ???

      override def getAllSkills(devId: String): IO[List[SkillData]] = IO.pure(Nil)

      override def getHiscoreSkillData(skill: Skill): IO[List[SkillData]] = IO.pure(Nil)

      override def getSkill(
        devId: String,
        skill: Skill
      ): IO[Option[SkillData]] =
        IO.pure(Some(SkillData(devId, "mike", skill, level = 1, xp = 2000)))

      override def awardSkillXP(
        devId: String,
        username: String,
        skill: Skill,
        xp: BigDecimal,
        level: Int
      ): IO[ValidatedNel[DatabaseErrors, DatabaseSuccess]] =
        IO.pure(Valid(UpdateSuccess))
    }

    val mockLangRepo = new LanguageRepositoryAlgebra[IO] {

      override def getAllLanguageData(): IO[List[LanguageData]] = ???
      override def getAllLanguages(devId: String): IO[List[LanguageData]] = IO.pure(Nil)
      override def getLanguage(devId: String, language: Language): IO[Option[LanguageData]] = IO.pure(None)
      override def getHiscoreLanguageData(language: Language): IO[List[LanguageData]] = IO.pure(Nil)
      override def awardLanguageXP(
        devId: String,
        username: String,
        language: Language,
        xp: BigDecimal,
        level: Int
      ): IO[ValidatedNel[DatabaseErrors, DatabaseSuccess]] =
        IO.pure(Valid(UpdateSuccess))
    }

    val service = new LevelServiceImpl[IO](mockSkillRepo, mockLangRepo)

    for {
      result <- service.awardLanguageXpWithLevel("dev123", "mike", Python, 2000)
    } yield expect(result == Valid(UpdateSuccess))
  }

}
