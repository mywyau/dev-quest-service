package configuration

import cats.Eq
import cats.effect.IO
import cats.syntax.eq.*
import configuration.AppConfig
import configuration.constants.IntegrationConfigConstants.*
import configuration.constants.LocalAppConfigConstants.*
import configuration.constants.ProdAppConfigConstants.*
import configuration.models.*
import io.circe.generic.auto.*
import io.circe.syntax.*
import weaver.SimpleIOSuite

object AppConfigSpec extends SimpleIOSuite {

  given Eq[DevSubmissionConfig] = Eq.fromUniversalEquals
  given Eq[RedisConfig] = Eq.fromUniversalEquals
  given Eq[S3Config] = Eq.fromUniversalEquals
  given Eq[PostgresqlConfig] = Eq.fromUniversalEquals
  given Eq[LocalAppConfig] = Eq.fromUniversalEquals
  given Eq[IntegrationSpecConfig] = Eq.fromUniversalEquals
  given Eq[FeatureSwitches] = Eq.fromUniversalEquals
  given Eq[AppConfig] = Eq.fromUniversalEquals

  val configReader: ConfigReaderAlgebra[IO] = ConfigReader[IO]

  def readerFor(env: String): ConfigReaderAlgebra[IO] =
    new ConfigReaderImpl[IO](new EnvProvider { def current = env })

  test("local - loads full local app config correctly") {
    for {
      config <- readerFor("local").loadAppConfig
    } yield expect.eql(localAppConfigConstant, config)
  }

  test("local - loads featureSwitches config correctly") {
    for {
      config <- readerFor("local").loadAppConfig
    } yield expect.eql(config.featureSwitches, localAppConfigConstant.featureSwitches)
  }

  test("prod - loads full prod app config correctly") {
    for {
      config <- readerFor("prod").loadAppConfig
    } yield expect.eql(prodAppConfigConstant, config)
  }

  test("prod - loads featureSwitches config correctly") {
    for {
      config <- readerFor("prod").loadAppConfig

    } yield expect.eql(config.featureSwitches, prodAppConfigConstant.featureSwitches)
  }

  test("integration - loads full integration app config correctly") {
    for {
      config <- readerFor("integration").loadAppConfig

    } yield expect.eql(integrationAppConfigConstant, config)
  }

  test("integration - loads featureSwitches config correctly") {
    for {
      config <- readerFor("integration").loadAppConfig

    } yield expect.eql(config.featureSwitches, integrationAppConfigConstant.featureSwitches)
  }
}
