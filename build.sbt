import com.softwaremill.PublishTravis.publishTravisSettings

lazy val scala212 = "2.12.10"
lazy val scala213 = "2.13.1"

lazy val supportedScalaVersions = List(scala212, scala213)

lazy val commonSettings = commonSmlBuildSettings ++ ossPublishSettings ++ Seq(
  organization := "com.softwaremill.correlator",
  crossScalaVersions := supportedScalaVersions
)

val scalaTest = "org.scalatest" %% "scalatest" % "3.2.5" % "test"

lazy val rootProject = (project in file("."))
  .settings(commonSettings: _*)
  .settings(
    publishArtifact := false,
    name := "root"
  )
  .settings(publishTravisSettings)
  .aggregate(monixLogback, monixLogbackHttp4s)

lazy val monixLogback: Project = (project in file("monix-logback"))
  .settings(commonSettings: _*)
  .settings(
    name := "monix-logback",
    libraryDependencies ++= Seq("io.monix" %% "monix" % "3.3.0", "ch.qos.logback" % "logback-classic" % "1.2.3", scalaTest)
  )

lazy val monixLogbackHttp4s: Project = (project in file("monix-logback-http4s"))
  .settings(commonSettings: _*)
  .settings(
    name := "monix-logback-http4s",
    libraryDependencies ++= Seq(
      "io.monix" %% "monix" % "3.3.0",
      "ch.qos.logback" % "logback-classic" % "1.2.3",
      "org.http4s" %% "http4s-core" % "0.21.19",
      "org.http4s" %% "http4s-dsl" % "0.21.19" % "test",
      scalaTest
    )
  )
  .dependsOn(monixLogback)
