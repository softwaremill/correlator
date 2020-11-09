import com.softwaremill.PublishTravis.publishTravisSettings

lazy val scala212 = "2.12.10"
lazy val scala213 = "2.13.1"

lazy val supportedScalaVersions = List(scala212, scala213)

lazy val commonSettings = commonSmlBuildSettings ++ ossPublishSettings ++ Seq(
  organization := "com.softwaremill.correlator",
  crossScalaVersions := supportedScalaVersions
)

val scalaTest = "org.scalatest" %% "scalatest" % "3.2.3" % "test"

lazy val rootProject = (project in file("."))
  .settings(commonSettings: _*)
  .settings(
    publishArtifact := false,
    name := "root"
  )
  .settings(publishTravisSettings)
  .aggregate(monixLogback, monixLogbackHttp4s)


// can be unified after 0.21 gets released
def http4sDependencies(scalaVersion: String): Seq[sbt.ModuleID] =
  if (scalaVersion.startsWith("2.12"))
    Seq("org.http4s" %% "http4s-core" % "0.21.8",
        "org.http4s" %% "http4s-dsl" % "0.21.8" % "test")
  else
    Seq("org.http4s" %% "http4s-core" % "0.21.6+43-2c1c1172-SNAPSHOT",
        "org.http4s" %% "http4s-dsl" % "0.21.6+43-2c1c1172-SNAPSHOT" % "test")

lazy val monixLogback : Project = (project in file("monix-logback"))
  .settings(commonSettings: _*)
  .settings(
    name := "monix-logback",
    libraryDependencies ++= Seq(
      "io.monix" %% "monix" % "3.3.0",
      "ch.qos.logback" % "logback-classic" % "1.2.3",
      scalaTest)
  )

lazy val monixLogbackHttp4s: Project = (project in file("monix-logback-http4s"))
  .settings(commonSettings: _*)
  .settings(
    name := "monix-logback-http4s",
    libraryDependencies ++= Seq(
      "io.monix" %% "monix" % "3.3.0",
      "ch.qos.logback" % "logback-classic" % "1.2.3",
      scalaTest) ++ http4sDependencies(scalaVersion.value)
    ).dependsOn(monixLogback)


