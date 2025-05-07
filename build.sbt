ThisBuild / version := "0.1.0-SNAPSHOT"
ThisBuild / scalaVersion := "3.3.4"
ThisBuild / parallelExecution := true

lazy val root = (project in file("."))
  .settings(
    name := "dev-quest-service",
    libraryDependencies ++= AppDependencies.compile ++ AppDependencies.test,
    Compile / run / fork := true,
    scalaSource := baseDirectory.value / "src" / "main" / "scala",
    Compile / unmanagedSourceDirectories += baseDirectory.value / "src" / "main" / "scala",
    Test / scalaSource := baseDirectory.value / "test" / "scala",

    // sbt-native-packager Docker settings
    dockerBaseImage := "eclipse-temurin:21-jre",
    dockerExposedPorts := Seq(8080),
    Compile / mainClass := Some("Main") // <-- Replace with your actual main class, e.g. "com.example.Main"
  )

lazy val it = (project in file("it"))
  .dependsOn(root)
  .settings(
    name := "dev-quest-service-it",
    libraryDependencies ++= AppDependencies.integrationTest,
    fork := true,
    parallelExecution := true,
    scalaSource := baseDirectory.value / "src" / "test" / "scala",
    Test / unmanagedSourceDirectories += baseDirectory.value / "src" / "test" / "scala"
  )

enablePlugins(ScalafmtPlugin)
enablePlugins(JavaAppPackaging, DockerPlugin)

