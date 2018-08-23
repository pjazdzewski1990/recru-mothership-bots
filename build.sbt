import Dependencies._

val akkaV = "2.5.13"
val akkaHttpV = "10.1.3"

lazy val root = (project in file(".")).
  settings(
    inThisBuild(List(
      organization := "io.scalac",
      scalaVersion := "2.12.6",
      version      := "0.1.0-SNAPSHOT"
    )),
    name := "mothership-bots",
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-actor" % akkaV,
      "com.typesafe.akka" %% "akka-http-spray-json" % akkaHttpV,
      "com.typesafe.akka" %% "akka-stream-kafka" % "0.22",
      "com.typesafe.play" %% "play-ahc-ws-standalone" % "2.0.0-M2",
      "com.typesafe"      %  "config" % "1.3.2",
      scalaTest % Test
    )
  )

//docker config
mainClass in Compile := Some("io.scalac.recru.BotsApp")

enablePlugins(JavaAppPackaging)
enablePlugins(DockerPlugin)
enablePlugins(AshScriptPlugin)

dockerEnvVars := Map(
  "KAFKA" -> "<insert value here using docker compose overwrites>",
  "API" -> "<insert value here using docker compose overwrites>")

dockerBaseImage := "openjdk:jre-alpine"