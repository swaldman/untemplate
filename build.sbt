ThisBuild / organization := "com.mchange"
ThisBuild / version      := "0.0.1-SNAPSHOT"

lazy val root = project
  .in(file("."))
  .enablePlugins(JavaAppPackaging)
  .settings(
    name                := "untemplate",
    scalaVersion        := "3.2.1",
    // scalacOptions       += "-explain",
    resolvers           += Resolver.mavenLocal,
    libraryDependencies += "dev.zio" %% "zio" % "2.0.5",
    libraryDependencies += "com.github.scopt" %% "scopt" % "4.1.0",
    libraryDependencies += "com.mchange" %% "literal" % "0.1.1-SNAPSHOT",
    libraryDependencies += "com.mchange" %% "codegenutil" % "0.0.1-SNAPSHOT"
  )

// sadly, as of sbt 1.x we need to stick with Scala 2.12.x
// so we can't directly just invoke Main.doIt(...), we have
// to exec a new Process
lazy val plugin = project
  .in(file("sbt-plugin"))
  .enablePlugins(SbtPlugin)
  .settings(
    resolvers += Resolver.mavenLocal,
    name := "untemplate-sbt-plugin"
  )


