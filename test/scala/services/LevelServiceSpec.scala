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

  // TODO: Either add a flag or use it to debug when needed
  // test(".calculateXpForLevel() - print XP values for levels 1 to 99") {
  //   val service = new LevelServiceImpl[IO](mockSkillRepo, mockLangRepo)

  //   val output = (1 to 99).toList.traverse { level =>
  //     IO {
  //       val xp = service.calculateXpForLevel(level).getOrElse(-1)
  //       f"Level $level%2d -> XP: $xp%,d"
  //     }
  //   }

  //   output.flatMap(_.traverse_(line => IO.println(line))).as(success)
  // }

  test(".awardSkillXpWithLevel() - returns correct XP and level") {

    val service = new LevelServiceImpl[IO](mockSkillRepo, mockLangRepo)

    for {
      result <- service.awardSkillXpWithLevel("dev123", "mike", Questing, 1000)
    } yield expect(result == Valid(UpdateSuccess))
  }

  test(".calculateLevel() - returns the correct level: '1', for 0 xp") {

    val service = new LevelServiceImpl[IO](mockSkillRepo, mockLangRepo)
    val resultantLevel = service.calculateLevel(0)

    IO(expect(resultantLevel == 1))
  }

  test(".calculateLevel() - returns the correct level: '1', for 1000 xp") {

    val service = new LevelServiceImpl[IO](mockSkillRepo, mockLangRepo)
    val resultantLevel = service.calculateLevel(1000.0)

    IO(expect(resultantLevel == 1))
  }

  test(".calculateLevel() - returns the correct level: '2', for 2564 xp") {

    val service = new LevelServiceImpl[IO](mockSkillRepo, mockLangRepo)
    val resultantLevel = service.calculateLevel(2564)

    IO(expect(resultantLevel == 2))
  }

  test(".calculateLevel() - returns the correct level: '3', for 7692 xp") {

    val service = new LevelServiceImpl[IO](mockSkillRepo, mockLangRepo)
    val resultantLevel = service.calculateLevel(7692)

    IO(expect(resultantLevel == 3))
  }

  test(".calculateLevel() - returns the correct level: '88', for 10,000,000 xp") {

    val service = new LevelServiceImpl[IO](mockSkillRepo, mockLangRepo)
    val resultantLevel = service.calculateLevel(10000000.0)

    IO(expect(resultantLevel == 88))
  }

  test(".calculateLevel() - returns the correct level: '99', for 15,000,000 xp") {

    val service = new LevelServiceImpl[IO](mockSkillRepo, mockLangRepo)
    val resultantLevel = service.calculateLevel(15000000.0)

    IO(expect(resultantLevel == 99))
  }

  test(".calculateLevel() - returns the correct level: '99', for 20,000,000 xp") {

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

  test(".generateLevelThresholds() - print XP values for levels 1 to 99") {
    val service = new LevelServiceImpl[IO](mockSkillRepo, mockLangRepo)

    for {
      result <- IO(service.generateLevelThresholds())
    } yield expect(
      result ==
        Vector(
          0, 2564, 7692, 15384, 25641, 38461, 53846, 71794, 92307, 115384, 141025, 169230, 200000, 233333, 269230, 307692, 348717, 392307, 438461, 487179, 538461, 592307, 648717, 707692, 769230,
          833333, 900000, 969230, 1041025, 1115384, 1192307, 1271794, 1353846, 1438461, 1525641, 1615384, 1707692, 1802564, 1900000, 2000000, 2102564, 2207692, 2315384, 2425641, 2538461, 2653846,
          2771794, 2892307, 3015384, 3141025, 3269230, 3400000, 3533333, 3669230, 3807692, 3948717, 4092307, 4238461, 4387179, 4538461, 4692307, 4848717, 5007692, 5169230, 5333333, 5500000, 5669230,
          5841025, 6015384, 6192307, 6371794, 6553846, 6738461, 6925641, 7115384, 7307692, 7502564, 7700000, 7900000, 8102564, 8307692, 8515384, 8725641, 8938461, 9153846, 9371794, 9592307, 9815384,
          10041025, 10269230, 10500000, 10832159, 11204098, 11616824, 12071292, 12568404, 13109024, 13693975, 14324048
        )
    )
  }

}
