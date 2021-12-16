import sbt._
import sbt.Keys._

object Dependencies {

  val coreDependencies: Seq[ModuleID] = Seq(
    "org.http4s"    %% "http4s-ember-server" % "0.23.7",
    "org.http4s"    %% "http4s-dsl"          % "0.23.7",
    "com.monovore"  %% "decline-effect"      % "2.2.0",
    "ch.qos.logback" % "logback-classic"     % "1.2.8"
  )

  val testDependencies: Seq[ModuleID] = Seq(
    "org.http4s"          %% "http4s-ember-client" % "0.23.7",
    "com.disneystreaming" %% "weaver-cats"         % "0.7.9",
    "com.disneystreaming" %% "weaver-scalacheck"   % "0.7.9"
  ).map(_ % Test)
}
