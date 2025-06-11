package configuration

import cats.effect.IO
import cats.syntax.eq.*
import cats.Eq
import configuration.models.*
import configuration.AppConfigConstants.*
import io.circe.generic.auto.*
import io.circe.syntax.*
import weaver.SimpleIOSuite

object AppConfigSpec extends SimpleIOSuite {

  given Eq[DevSubmissionConfig] = Eq.fromUniversalEquals
  given Eq[RedisConfig] = Eq.fromUniversalEquals
  given Eq[S3Config] = Eq.fromUniversalEquals
  given Eq[PostgresqlConfig] = Eq.fromUniversalEquals
  given Eq[LocalConfig] = Eq.fromUniversalEquals
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
    } yield expect.eql(config.localConfig, appConfig.localConfig)
  }

  test("loads integrationSpecConfig correctly") {
    for {
      config <- configReader.loadAppConfig
    } yield expect.eql(config.integrationSpecConfig, appConfig.integrationSpecConfig)
  }
}
