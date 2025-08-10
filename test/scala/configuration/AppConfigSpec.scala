package configuration

import cats.Eq
import cats.effect.IO
import cats.syntax.eq.*
import configuration.constants.LocalAppConfigConstants.*
import configuration.constants.ProdAppConfigConstants.*
import configuration.constants.IntegrationConfigConstants.*
import configuration.constants.AppConfigConstants.*
import configuration.models.*
import io.circe.generic.auto.*
import io.circe.syntax.*
import weaver.SimpleIOSuite

import configuration.AppConfig

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

  test("loads full app config correctly") {
    for {
      config <- configReader.loadAppConfig
    } yield expect.eql(appConfigConstant, config)
  }

  test("loads featureSwitches config correctly") {
    for {
      config <- configReader.loadAppConfig
    } yield expect.eql(config.featureSwitches, appConfigConstant.featureSwitches)
  }

  test("loads localConfig correctly") {
    for {
      config <- configReader.loadAppConfig
    } yield expect.eql(config.localAppConfig, appConfigConstant.localAppConfig)
  }

  test("loads integrationSpecConfig correctly") {
    for {
      config <- configReader.loadAppConfig
    } yield expect.eql(config.integrationSpecConfig, appConfigConstant.integrationSpecConfig)
  }
}
