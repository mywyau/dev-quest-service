package configuration

import cats.Eq
import cats.effect.IO
import cats.syntax.eq.*
import configuration.models.*
import io.circe.generic.auto.*
import io.circe.syntax.*
import weaver.SimpleIOSuite

import configuration.AppConfig
import configuration.models.DevSubmissionConfig
import configuration.AppConfigConstants.*

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
    } yield expect.eql(appConfig, config)
  }

  test("loads featureSwitches config correctly") {
    for {
      config <- configReader.loadAppConfig
    } yield expect.eql(config.featureSwitches, appConfig.featureSwitches)
  }

  test("loads localConfig correctly") {
    for {
      config <- configReader.loadAppConfig
    } yield expect.eql(config.localAppConfig, appConfig.localAppConfig)
  }

  test("loads integrationSpecConfig correctly") {
    for {
      config <- configReader.loadAppConfig
    } yield expect.eql(config.integrationSpecConfig, appConfig.integrationSpecConfig)
  }
}
