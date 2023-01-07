ThisBuild / organization := "com.mchange"
ThisBuild / version      := "0.0.1-SNAPSHOT"

val ZIOVersion = "2.0.5"

lazy val root = project
  .in(file("."))
  .enablePlugins(JavaAppPackaging)
  .settings (
    name                := "untemplate",
    scalaVersion        := "3.2.1",
    // scalacOptions       += "-explain",
    resolvers           += Resolver.mavenLocal,
    libraryDependencies += "dev.zio" %% "zio" % ZIOVersion,
    libraryDependencies += "com.github.scopt" %% "scopt" % "4.1.0",
    libraryDependencies += "com.mchange" %% "literal" % "0.1.1-SNAPSHOT",
    libraryDependencies += "com.mchange" %% "codegenutil" % "0.0.1-SNAPSHOT"
  )

// sadly, as of sbt 1.x we need to stick with Scala 2.12.x
// so we can't directly just invoke Main.doIt(...), we have
// to exec a new Process
//
// the code has no direct dependency on the root project,
// though indirectly it depends on its executable.
// So, no dependsOn(root), except maybe in a deeper sense.
//
lazy val plugin = project
  .in(file("sbt-plugin"))
  .enablePlugins(SbtPlugin)
  .settings (
    resolvers += Resolver.mavenLocal,
    name := "untemplate-sbt-plugin"
  )

val MillVersion = "0.10.10"

lazy val mill = project
  .in(file("mill"))
  .dependsOn(root)
  .settings (
    name := "untemplate-mill",
    scalaVersion        := "2.13.10",
    scalacOptions       += "-deprecation",
    scalacOptions       += "-Ytasty-reader",
    resolvers += Resolver.mavenLocal,
    libraryDependencies += "com.lihaoyi" %% "mill-main" % MillVersion,
    libraryDependencies += "com.lihaoyi" %% "mill-scalalib" % MillVersion
  )


