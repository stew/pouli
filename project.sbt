import sbt._
import Keys._

scalaVersion in Global := "2.10.4"

lazy val core = project

lazy val benchmark = project.dependsOn(core)

lazy val pouli = project.in(file(".")).aggregate(core,benchmark)
