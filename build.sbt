val ScalaVersion = "3.2.1"

lazy val root = project
  .in(file("."))
  .settings(
    organization        := "com.mchange",
    name                := "untemplate",
    version             := "0.0.1-SNAPSHOT",
    scalaVersion        := ScalaVersion,
    // scalacOptions       += "-explain",
    resolvers           += Resolver.mavenLocal,
    libraryDependencies += "dev.zio" %% "zio" % "2.0.5",
    libraryDependencies += "com.github.scopt" %% "scopt" % "4.1.0",
    libraryDependencies += "com.mchange" %% "literal" % "0.1.1-SNAPSHOT",
    libraryDependencies += "com.mchange" %% "codegenutil" % "0.0.1-SNAPSHOT"
)

