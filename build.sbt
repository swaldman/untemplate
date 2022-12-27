val ScalaVersion = "3.2.1"

lazy val root = project
  .in(file("."))
  .settings(
    organization        := "com.mchange",
    name                := "untemplate",
    version             := "0.0.1-SNAPSHOT",
    scalaVersion        := ScalaVersion,
    resolvers += Resolver.mavenLocal,
    libraryDependencies += "dev.zio" %% "zio" % "2.0.5"
  )

