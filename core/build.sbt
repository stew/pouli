name := "pouli-core"

scalacOptions := Seq(
  "-feature",
  "-deprecation",
  "-encoding", "utf8",
  "-language:postfixOps",
  "-language:higherKinds",
  "-target:jvm-1.7",
  "-unchecked",
  "-Xcheckinit",
  "-Xfuture",
  "-Xlint",
  "-Xfatal-warnings",
  "-Yno-adapted-args",
  "-Ywarn-dead-code",
  "-Ywarn-value-discard",
  "-Yno-predef",
  "-Yno-imports"
)

scalacOptions in Test := Seq(
  "feature",
  "-language:reflectiveCalls"
)

libraryDependencies ++= Seq(
  "org.scalatest"  % "scalatest_2.11"     % "2.2.1"  % "test",
  "org.scalacheck" %% "scalacheck"        % "1.10.1" % "test",
  "org.scalaz"     %% "scalaz-concurrent" % "7.2.0-SNAPSHOT",
  "org.scalaz"     %% "scalaz-core"       % "7.2.0-SNAPSHOT")

