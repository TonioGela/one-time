import Dependencies._
import Settings._

inScope(Global)(globalSettings)
inThisBuild(scalaFixSettings ++ sbtGithubActionsSettings ++ publicSettings)

lazy val `one-time` = (project in file("."))
  .settings(name := "one-time", commonSettings, libraryDependencies ++= coreDependencies ++ testDependencies)
  .enablePlugins(NativeImagePlugin).settings(
    Compile / mainClass := Some("dev.toniogela.Main"),
    nativeImageOptions ++= List(
      "-H:+AddAllCharsets",
      "--allow-incomplete-classpath",
      "--no-fallback",
      "--enable-http",
      "--enable-https",
      "--static"
    )
  )
