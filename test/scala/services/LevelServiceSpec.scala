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

  val mockSkillRepo = new DevSkillRepositoryAlgebra[IO] {

    override def countForSkill(skill: Skill): IO[Int] = ???

    override def getPaginatedSkillData(skill: Skill, offset: Int, limit: Int): IO[List[SkillData]] = ???

    override def getSkillsForUser(username: String): IO[List[SkillData]] = ???

    override def getAllSkillData(): IO[List[SkillData]] = ???

    override def getAllSkills(devId: String): IO[List[DevSkillData]] = IO.pure(Nil)

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
      level: Int,
      nextLevel: Int,
      nextLevelXp: BigDecimal
    ): IO[ValidatedNel[DatabaseErrors, DatabaseSuccess]] =
      IO.pure(Valid(UpdateSuccess))
  }

  val mockLangRepo = new DevLanguageRepositoryAlgebra[IO] {

    override def countForLanguage(language: Language): IO[Int] = ???

    override def getPaginatedLanguageData(language: Language, offset: Int, limit: Int): IO[List[LanguageData]] = ???

    override def getLanguagesForUser(username: String): IO[List[LanguageData]] = ???

    override def getAllLanguageData(): IO[List[LanguageData]] = ???
    override def getAllLanguages(devId: String): IO[List[DevLanguageData]] = IO.pure(Nil)
    override def getLanguage(devId: String, language: Language): IO[Option[LanguageData]] = IO.pure(None)
    override def getHiscoreLanguageData(language: Language): IO[List[LanguageData]] = IO.pure(Nil)
    override def awardLanguageXP(
      devId: String,
      username: String,
      language: Language,
      xp: BigDecimal,
      level: Int,
      nextLevel: Int,
      nextLevelXp: BigDecimal
    ): IO[ValidatedNel[DatabaseErrors, DatabaseSuccess]] =
      IO.pure(Valid(UpdateSuccess))
  }

  test(".awardSkillXpWithLevel() - returns correct XP and level") {

    val service = new LevelServiceImpl[IO](mockSkillRepo, mockLangRepo)

    for {
      result <- service.awardSkillXpWithLevel("dev123", "mike", Questing, 1000)
    } yield expect(result == Valid(UpdateSuccess))
  }

  test(".calculateLevel() - returns the correct level: '6', for 1000 xp") {

    val service = new LevelServiceImpl[IO](mockSkillRepo, mockLangRepo)
    val resultantLevel = service.calculateLevel(1000.0)

    IO(expect(resultantLevel == 1))
  }

  test(".calculateLevel() - returns the correct level: '93', for 10,000,000 xp") {

    val service = new LevelServiceImpl[IO](mockSkillRepo, mockLangRepo)
    val resultantLevel = service.calculateLevel(10000000.0)

    IO(expect(resultantLevel == 88))
  }

  test(".calculateLevel() - returns the correct level: '97', for 15,000,000 xp") {

    val service = new LevelServiceImpl[IO](mockSkillRepo, mockLangRepo)
    val resultantLevel = service.calculateLevel(15000000.0)

    IO(expect(resultantLevel == 99))
  }

  test(".calculateLevel() - returns the correct level: '100', for 20,000,000 xp") {

    val service = new LevelServiceImpl[IO](mockSkillRepo, mockLangRepo)
    val resultantLevel = service.calculateLevel(20000000.0)

    IO(expect(resultantLevel == 99))
  }

  test(".awardSkillXpWithLevel() - returns correct XP and level") {
    
    val service = new LevelServiceImpl[IO](mockSkillRepo, mockLangRepo)

    for {
      result <- service.awardLanguageXpWithLevel("dev123", "mike", Python, 2000)
    } yield expect(result == Valid(UpdateSuccess))
  }

}
