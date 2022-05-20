/* IDEA notes
 * May require to delete .idea and re-import with all checkboxes
 * Worksheets may not work: https://youtrack.jetbrains.com/issue/SCL-6726
 * To work with worksheets, make sure:
   1. You've selected the appropriate project
   2. You've checked "Make project before run"
 */

import sbt.Keys._
import sbt._
import sbtcrossproject.CrossPlugin.autoImport.{CrossType, crossProject}


lazy val lang =
  crossProject(JVMPlatform)
    .withoutSuffixFor(JVMPlatform)
    .crossType(CrossType.Full)
    .settings(
      coverageExcludedPackages := ".*",
      test in assembly := {},
      libraryDependencies ++= Dependencies.lang.value ++ Dependencies.test,
      inConfig(Compile)(
        Seq(
          sourceGenerators += Tasks.docSource,
          PB.targets += scalapb.gen(flatPackage = true) -> (sourceManaged in Compile).value,
          PB.protoSources := Seq(baseDirectory.value.getParentFile / "shared" / "src" / "main" / "protobuf"),
          PB.deleteTargetDirectory := false
        )
      )
    )

lazy val `lang-jvm` = lang.jvm
  .settings(
    name := "RIDE Compiler",
    normalizedName := "lang",
    description := "The RIDE smart contract language compiler",
    libraryDependencies += "org.scala-js" %% "scalajs-stubs" % "1.0.0" % Provided
  )

lazy val `lang-testkit` = project
  .dependsOn(`lang-jvm`)
  .in(file("lang/testkit"))
  .settings(
    assemblyMergeStrategy in assembly := {
      case PathList("META-INF", xs @ _*) => MergeStrategy.discard
      case x => MergeStrategy.first
    }
  )
  .settings(
    libraryDependencies ++= Dependencies.test.map(_.withConfigurations(Some("compile")))
  )

lazy val `lang-tests` = project.in(file("lang/tests")).dependsOn(`lang-testkit`)
  .settings(
    assemblyMergeStrategy in assembly := {
      case PathList("META-INF", xs @ _*) => MergeStrategy.discard
      case x => MergeStrategy.first
    }
  )
  .settings(test in assembly := {})

lazy val `lang-doc` = project
  .in(file("lang/doc"))
  .dependsOn(`lang-jvm`)
  .settings(
    libraryDependencies ++= Seq("com.github.spullara.mustache.java" % "compiler" % "0.9.5") ++ Dependencies.test
  )

lazy val node = project.dependsOn(`lang-jvm`, `lang-testkit` % "test")

lazy val `grpc-server`    = project.dependsOn(node % "compile;test->test;runtime->provided")
lazy val `node-it`        = project.dependsOn(node, `grpc-server`)
lazy val `node-generator` = project.dependsOn(node, `node` % "compile")
lazy val benchmark        = project.dependsOn(node % "compile;test->test")

lazy val `blockchain-updates` = project.dependsOn(node % "compile;test->test;runtime->provided")

lazy val root = (project in file("."))
  .aggregate(
    /*`lang-js`,*/
    `lang-jvm`,
    `lang-testkit`,
    `lang-tests`,
    node,
    `node-it`,
    `node-generator`,
    //benchmark,
    `blockchain-updates`
  )

// this a hack to support both `node-it/test` and `it/test` commands
lazy val it = project.aggregate(`node-it`)

inScope(Global)(
  Seq(
    scalaVersion := "2.13.3",
    organization := "Aeneas",
    organizationName := "Aeneas Platform",
    V.fallback := (1, 2, 6),
    organizationHomepage := Some(url("https://aeneas.pm")),
    scmInfo := Some(ScmInfo(url("https://github.com/AeneasPlatform/aeneasrework/"), "git@github.com:AeneasPlatform/aeneasrework.git", None)),
    licenses := Seq(("MIT", url("https://github.com/AeneasPlatform/aeneasrework/blob/master/LICENSE"))),
    scalacOptions ++= Seq(
      "-feature",
      "-language:higherKinds",
      "-language:implicitConversions",
      "-language:postfixOps",
      "-opt:l:inline",
      "-opt-inline-from:**"
    ),
    crossPaths := false,
    scalafmtOnCompile := false,
    dependencyOverrides ++= Dependencies.enforcedVersions.value,
    cancelable := true,
    logBuffered := false,
    coverageExcludedPackages := ".*",
    parallelExecution := false,
    testListeners := Seq.empty, // Fix for doubled test reports
    /* http://www.scalatest.org/user_guide/using_the_runner
     * o - select the standard output reporter
     * I - show reminder of failed and canceled tests without stack traces
     * D - show all durations
     * O - drop InfoProvided events
     * F - show full stack traces
     * u - select the JUnit XML reporter with output directory
     */
    testOptions += Tests.Argument("-oIDOF", "-u", "target/test-reports"),
    testOptions += Tests.Setup(_ => sys.props("sbt-testing") = "true"),
    network := Network(sys.props.get("network")),
    resolvers += Resolver.sonatypeRepo("snapshots"),
    sources in (Compile, doc) := Seq.empty,
    publishArtifact in (Compile, packageDoc) := false
  )
)

// ThisBuild options
git.useGitDescribe := true
git.uncommittedSignifier := Some("DIRTY")

assemblyMergeStrategy in assembly := {
  case PathList("META-INF", xs @ _*) => MergeStrategy.discard
  case x => MergeStrategy.first
}

lazy val packageAll = taskKey[Unit]("Package all artifacts")
packageAll := Def
  .sequential(
    root / clean,
    Def.task {
      (node / assembly).value
/*
      (node / Debian / packageBin).value
      (`grpc-server` / Universal / packageZipTarball).value
      (`grpc-server` / Debian / packageBin).value
*/
    }
  )
  .value

lazy val checkPRRaw = taskKey[Unit]("Build a project and run unit tests")
checkPRRaw := Def
  .sequential(
    root / clean/*,
    Def.task {
      (Test / compile).value
      (`lang-tests` / Test / test).value
      //(`lang-js` / Compile / fastOptJS).value
      (node / Test / test).value
    }*/
  )
  .value

def checkPR: Command = Command.command("checkPR") { state =>
  val updatedState = Project
    .extract(state)
    .appendWithoutSession(Seq(Global / scalacOptions ++= Seq("-Xfatal-warnings")), state)
  Project.extract(updatedState).runTask(checkPRRaw, updatedState)
  state
}

commands += checkPR
