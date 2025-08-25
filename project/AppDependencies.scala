import sbt.*

object AppDependencies {

  // Define versions for libraries
  val catsCoreVersion = "2.10.0"
  val catsEffectVersion = "3.5.1"
  val http4sVersion = "0.23.28"
  val doobieVersion = "1.0.0-RC4"
  val passayVersion = "1.6.1"
  val jwtCirceVersion = "9.0.5"
  val redis4catsVersion = "1.7.1"
  val circeVersion = "0.14.7"
  val scalatestVersion = "3.2.15"
  val weaverVersion = "0.8.3"
  val flywayVersion = "8.5.0"
  val Fs2KafkaV = "3.3.0"          // recent

  // Compile dependencies
  val compile: Seq[ModuleID] = Seq(
    "com.github.fd4s" %% "fs2-kafka" % Fs2KafkaV,
    "org.typelevel" %% "log4cats-slf4j" % "2.6.0",
    "ch.qos.logback" % "logback-classic" % "1.5.6" exclude ("org.slf4j", "slf4j-jdk14"),
    "org.typelevel" %% "cats-core" % catsCoreVersion,
    "org.typelevel" %% "cats-effect" % catsEffectVersion,
    "org.http4s" %% "http4s-dsl" % http4sVersion,
    "org.http4s" %% "http4s-ember-server" % http4sVersion,
    "org.http4s" %% "http4s-circe" % http4sVersion,
    "org.http4s" %% "http4s-jawn" % http4sVersion,
    "com.auth0" % "java-jwt" % "4.4.0",
    "org.tpolecat" %% "doobie-core" % doobieVersion,
    "org.tpolecat" %% "doobie-hikari" % doobieVersion,
    "org.tpolecat" %% "doobie-postgres" % doobieVersion,
    "org.tpolecat" %% "doobie-postgres-circe" % doobieVersion, // this one is needed
    "org.passay" % "passay" % passayVersion,
    "com.github.jwt-scala" %% "jwt-circe" % jwtCirceVersion,
    "dev.profunktor" %% "redis4cats-effects" % redis4catsVersion,
    "io.circe" %% "circe-generic" % circeVersion,
    "io.circe" %% "circe-core" % circeVersion,
    "io.circe" %% "circe-parser" % circeVersion,
    "org.http4s" %% "http4s-ember-client" % "0.23.28",
    "com.github.pureconfig" %% "pureconfig-core" % "0.17.8",
    "software.amazon.awssdk" % "s3" % "2.25.0",
    "com.stripe" % "stripe-java" % "29.2.0", // or latest
    "io.github.cdimascio" % "dotenv-java" % "3.0.0"
  )

  // Test dependencies
  val test: Seq[ModuleID] = Seq(
    "org.scalatest" %% "scalatest" % scalatestVersion % Test,
    "org.tpolecat" %% "doobie-scalatest" % doobieVersion % Test,
    "com.disneystreaming" %% "weaver-cats" % weaverVersion % Test,
    "com.disneystreaming" %% "weaver-scalacheck" % "0.7.6" % Test,
    "org.http4s" %% "http4s-ember-client" % "0.23.28" % Test,
    "org.scalamock" %% "scalamock" % "7.3.2" % Test
  )

  // Integration test dependencies
  val integrationTest: Seq[ModuleID] = Seq(
    "com.github.fd4s" %% "fs2-kafka" % Fs2KafkaV % Test,
    "org.tpolecat" %% "doobie-h2" % doobieVersion % Test,
    "org.flywaydb" % "flyway-core" % flywayVersion,
    "com.disneystreaming" %% "weaver-cats" % weaverVersion % Test,
    "org.http4s" %% "http4s-ember-client" % "0.23.28" % Test,
    "com.disneystreaming" %% "weaver-scalacheck" % "0.7.6" % Test,
    "com.github.pureconfig" %% "pureconfig-core" % "0.17.8" % Test,
    "org.scalamock" %% "scalamock" % "7.3.2" % Test
  )

  // Additional workaround for macOS if needed
  def macOsWorkaround(): Seq[ModuleID] =
    if (sys.props.get("os.name").exists(_.toLowerCase.contains("mac"))) {
      Seq("org.reactivemongo" % "reactivemongo-shaded-native" % "0.20.3-osx-x86-64" % Test)
    } else Seq()

  // Aggregate all dependencies
  def apply(): Seq[ModuleID] = compile ++ test ++ integrationTest ++ macOsWorkaround()
}
