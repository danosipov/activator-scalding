// This SBT build file adapted from https://github.com/deanwampler/scalding-workshop.

import sbt._
import sbt.Keys._

object BuildSettings {

  val Name = "activator-scalding"
  val Version = "1.0.0"
  val ScalaVersion = "2.10.3"

  import Scalding._

  val basicSettings = Defaults.defaultSettings ++ scaldingSettings ++ Seq (
    name          := Name,
    version       := Version,
    scalaVersion  := ScalaVersion,
    organization  := "com.typesafe",
    description   := "Activator Scalding Template",
    scalacOptions := Seq("-deprecation", "-unchecked", "-encoding", "utf8")
  )

  // sbt-assembly settings for building a fat jar that includes all dependencies.
  // This is useful for running Hadoop jobs, but not needed for local script testing.
  // Adapted from https://github.com/snowplow/scalding-example-project
  import sbtassembly.Plugin._
  import AssemblyKeys._
  lazy val sbtAssemblySettings = assemblySettings ++ Seq(

    // Slightly cleaner jar name
    jarName in assembly := s"${name}-${version}.jar"  ,
    
    // Drop these jars, most of which are dependencies of dependencies and already exist
    // in Hadoop deployments or aren't needed for local mode execution.
    excludedJars in assembly <<= (fullClasspath in assembly) map { cp =>
      val excludes = Set(
        "scala-compiler.jar",
        "jsp-api-2.1-6.1.14.jar",
        "jsp-2.1-6.1.14.jar",
        "jasper-compiler-5.5.12.jar",
        "minlog-1.2.jar", // Otherwise causes conflicts with Kyro (which Scalding pulls in)
        "janino-2.5.16.jar", // Janino includes a broken signature, and is not needed anyway
        "commons-beanutils-core-1.8.0.jar", // Clash with each other and with commons-collections
        "commons-beanutils-1.7.0.jar"
      ) 
      cp filter { jar => excludes(jar.data.getName) }
    },
    
    mergeStrategy in assembly <<= (mergeStrategy in assembly) {
      (old) => {
        case "project.clj" => MergeStrategy.discard // Leiningen build files
        case x => old(x)
      }
    }
  )

  lazy val buildSettings = basicSettings ++ sbtAssemblySettings
}


object Resolvers {
  val typesafe = "Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/"
  val sonatype = "Sonatype Release" at "https://oss.sonatype.org/content/repositories/releases"
  val mvnrepository = "MVN Repo" at "http://mvnrepository.com/artifact"
  val conjars  = "Concurrent Maven Repo" at "http://conjars.org/repo"
  val clojars  = "Clojars Repo" at "http://clojars.org/repo"
  val twitterMaven = "Twitter Maven" at "http://maven.twttr.com"
  
  val allResolvers = Seq(typesafe, sonatype, mvnrepository, conjars, clojars, twitterMaven)

}

object Dependency {
  object Version {
    val Scalding = "0.9.0rc4"
    val Algebird = "0.2.0"
    val Hadoop   = "1.1.2"  // Fairly old, but reliable version. Substitute your "favorite"
  }

  // ---- Application dependencies ----

  // Include the Scala compiler itself for reification and evaluation of expressions. 
  val scalaCompiler  = "org.scala-lang" %  "scala-compiler" % BuildSettings.ScalaVersion
  
  val scalding_args  = "com.twitter"    %% "scalding-args"  % Version.Scalding
  val scalding_core  = "com.twitter"    %% "scalding-core"  % Version.Scalding
  val scalding_date  = "com.twitter"    %% "scalding-date"  % Version.Scalding
  val algebird_core  = "com.twitter"    %% "algebird-core"  % Version.Algebird
  val algebird_util  = "com.twitter"    %% "algebird-util"  % Version.Algebird

  val hadoop_core    = "org.apache.hadoop" % "hadoop-core"  % Version.Hadoop
}

object Dependencies {
  import Dependency._

  val activatorscalding = Seq(
    scalaCompiler, scalding_args, scalding_core, scalding_date, 
    algebird_core, algebird_util, hadoop_core)
}

object ActivatorScaldingBuild extends Build {
  import Resolvers._
  import Dependencies._
  import BuildSettings._

  lazy val activatorscalding = Project(
    id = "Activator-Scalding",
    base = file("."),
    settings = buildSettings ++ Seq(
      // runScriptSetting, 
      resolvers := allResolvers,
      libraryDependencies ++= Dependencies.activatorscalding))
}



