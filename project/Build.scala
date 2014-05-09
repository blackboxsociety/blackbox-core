import sbt._
import sbt.Keys._

import bintray.Plugin._
import bintray.Keys._

object Build extends Build {

  val customBintraySettings = bintrayPublishSettings ++ Seq(
    packageLabels in bintray       := Seq("evented", "reactive", "network"),
    bintrayOrganization in bintray := Some("blackboxsociety"),
    repository in bintray          := "releases"
  )

  val root = Project("root", file("."))
    .settings(customBintraySettings: _*)
    .settings(
      name                  := "blackbox-core",
      organization          := "com.blackboxsociety",
      version               := "0.1.0",
      scalaVersion          := "2.11.0",
      licenses              += ("MIT", url("http://opensource.org/licenses/MIT")),
      //scalacOptions       += "-feature",
      //scalacOptions       += "-deprecation",
      scalacOptions in Test ++= Seq("-Yrangepos"),
      resolvers             += "Typesafe repository" at "http://repo.typesafe.com/typesafe/releases/",
      resolvers             ++= Seq("snapshots", "releases").map(Resolver.sonatypeRepo),
      libraryDependencies   += "org.scalaz" %% "scalaz-core" % "7.0.6",
      libraryDependencies   += "org.scalaz" %% "scalaz-effect" % "7.0.6",
      libraryDependencies   += "org.scalaz" %% "scalaz-concurrent" % "7.0.6",
      libraryDependencies   += "org.scalaz" %% "scalaz-iteratee" % "7.0.6",
      libraryDependencies   += "org.specs2" %% "specs2" % "2.3.11" % "test"
    )

}