import com.softwaremill.PublishTravis.publishTravisSettings

lazy val scala212 = "2.12.10"
lazy val scala213 = "2.13.1"

lazy val supportedScalaVersions = List(scala212, scala213)

lazy val commonSettings = commonSmlBuildSettings ++ ossPublishSettings ++ Seq(
  organization := "com.softwaremill.correlator",
  crossScalaVersions := supportedScalaVersions,
  addCompilerPlugin("com.olegpy" %% "better-monadic-for" % "0.3.1")
)

val scalaTest = "org.scalatest" %% "scalatest"       % "3.0.8" % Test
val logback   = "ch.qos.logback" % "logback-classic" % "1.2.3"
val http4s =
  ((version: String) => Seq(
    "org.http4s" %% "http4s-core" % version,
    "org.http4s" %% "http4s-dsl" % version % Test)
  )("0.21.0-M5")

lazy val rootProject = (project in file("."))
  .settings(name := "correlator")
  .settings(commonSettings: _*)
  .settings(publishArtifact := false)
  .settings(publishTravisSettings)
  .aggregate(monixLogbackHttp4s, zioLogbackHttp4s)

lazy val monixLogbackHttp4s: Project = (project in file("monix-logback-http4s"))
  .settings(commonSettings: _*)
  .settings(
    name := "monix-logback-http4s",
    libraryDependencies ++= Seq(
      "io.monix" %% "monix" % "3.1.0",
      logback,
      scalaTest
    ) ++ http4s
  )

lazy val zioLogbackHttp4s: Project = (project in file("zio-logback-http4s"))
  .settings(commonSettings: _*)
  .settings(
    libraryDependencies ++= Seq(
      compilerPlugin("com.github.ghik" % "silencer-plugin" % "1.4.4" cross CrossVersion.full),
      "com.github.ghik" % "silencer-lib" % "1.4.4" % Provided cross CrossVersion.full
    )
  )
  .settings(
    name := "zio-logback-http4s",
    libraryDependencies ++= Seq(
      "dev.zio" %% "zio" % "1.0.0-RC17",
      "dev.zio" %% "zio-interop-cats" % "2.0.0.0-RC8",
      logback,
      scalaTest
    ) ++ http4s
  )
