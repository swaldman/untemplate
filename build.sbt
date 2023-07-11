ThisBuild / publishTo := {
    if (isSnapshot.value) Some(Resolver.url("sonatype-snapshots", url("https://oss.sonatype.org/content/repositories/snapshots")))
    else Some(Resolver.url("sonatype-staging", url("https://oss.sonatype.org/service/local/staging/deploy/maven2")))
}

ThisBuild / organization := "com.mchange"
ThisBuild / version      := "0.0.5-SNAPSHOT"

val ZIOVersion = "2.0.5"

lazy val root = project
  .in(file("."))
  .enablePlugins(JavaAppPackaging)
  .settings (
    name                     := "untemplate",
    scalaVersion             := "3.2.1",
    // scalacOptions       += "-explain",
    resolvers                += Resolver.mavenLocal,
    libraryDependencies      += "dev.zio" %% "zio" % ZIOVersion,
    libraryDependencies      += "com.github.scopt" %% "scopt" % "4.1.0",
    libraryDependencies      += "com.mchange" %% "literal" % "0.1.2",
    libraryDependencies      += "com.mchange" %% "codegenutil" % "0.0.2",
    Compile/sourceGenerators += generateBuildInfoSourceGeneratorTask,
    pomExtra                 := pomExtraForProjectName_Apache2( name.value )
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
    libraryDependencies += "com.lihaoyi" %% "mill-scalalib" % MillVersion,
    pomExtra            := pomExtraForProjectName_Apache2( name.value )
  )

def pomExtraForProjectName_Apache2( projectName : String ) = {
    <url>https://github.com/swaldman/{projectName}</url>
      <licenses>
          <license>
              <name>The Apache Software License, Version 2.0</name>
              <url>http://www.apache.org/licenses/LICENSE-2.0.txt</url>
              <distribution>repo</distribution>
          </license>
      </licenses>
      <scm>
          <url>https://github.com/swaldman/{projectName}</url>
          <connection>scm:git:git@github.com:swaldman/{projectName}.git</connection>
      </scm>
      <developers>
          <developer>
              <id>swaldman</id>
              <name>Steve Waldman</name>
              <email>swaldman@mchange.com</email>
          </developer>
      </developers>
}

val BuildInfoPackageDir = Vector( "untemplate", "build" )

val generateBuildInfoSourceGeneratorTask = Def.task{
  import java.io._
  import java.nio.file.Files

  import scala.io.Codec

  import java.time._
  import java.time.format._

  val srcManaged       = (Compile/sourceManaged).value
  val UntemplateVersion = version.value

  val now = Instant.now
  val ts = DateTimeFormatter.RFC_1123_DATE_TIME.withZone( ZoneId.systemDefault() ).format(now)

  val sep = File.separator
  val outDir = new File( srcManaged, BuildInfoPackageDir.mkString( sep ) )
  val outFile = new File( outDir, "generated.scala" )
  val contents = {
    s"""|package ${BuildInfoPackageDir.mkString(".")}
        |
        |// has to be backward compatible to Scala 2.13 for mill
        |object BuildInfo {
        |  val UntemplateVersion = "${UntemplateVersion}"
        |  val BuildTimestamp    = "${ts}"
        |}
        |""".stripMargin
  }
  outDir.mkdirs()
  Files.write( outFile.toPath, contents.getBytes( Codec.UTF8.charSet ) )
  outFile :: Nil
}



