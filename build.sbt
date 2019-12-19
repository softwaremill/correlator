import com.softwaremill.PublishTravis.publishTravisSettings

lazy val scala212 = "2.12.10"
lazy val scala213 = "2.13.1"

lazy val supportedScalaVersions = List(scala212, scala213)

lazy val commonSettings = commonSmlBuildSettings ++ ossPublishSettings ++ Seq(
  organization := "com.softwaremill.correlator",
  crossScalaVersions := supportedScalaVersions
)

val scalaTest = "org.scalatest" %% "scalatest" % "3.1.0" % "test"

lazy val rootProject = (project in file("."))
  .settings(commonSettings: _*)
  .settings(
    publishArtifact := false,
    name := "root"
  )
  .settings(publishTravisSettings)
  .aggregate(monixLogbackHttp4s)


// can be unified after 0.21 gets released
def http4sDependencies(scalaVersion: String): Seq[sbt.ModuleID] =
  if (scalaVersion.startsWith("2.12"))
    Seq("org.http4s" %% "http4s-core" % "0.20.11",
        "org.http4s" %% "http4s-dsl" % "0.20.11" % "test")
  else
    Seq("org.http4s" %% "http4s-core" % "0.21.0-M6",
        "org.http4s" %% "http4s-dsl" % "0.21.0-M6" % "test")

lazy val monixLogbackHttp4s: Project = (project in file("monix-logback-http4s"))
  .settings(commonSettings: _*)
  .settings(
    name := "monix-logback-http4s",
    libraryDependencies ++= Seq(
      "io.monix" %% "monix" % "3.1.0",
      "ch.qos.logback" % "logback-classic" % "1.2.3",
      scalaTest) ++ http4sDependencies(scalaVersion.value)
    )


