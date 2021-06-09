
libraryDependencies ++= Dependencies.console :+ Dependencies.janino :+ Dependencies.asyncHttpClient :+ Dependencies.logback
assemblyMergeStrategy in assembly := {
  case PathList("META-INF", xs @ _*) => MergeStrategy.discard
  case x => MergeStrategy.first
}

fork in run := true
