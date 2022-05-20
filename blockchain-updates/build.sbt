

name := "blockchain-updates"

libraryDependencies ++= Dependencies.kafka +: Dependencies.protobuf.value

extensionClasses += "com.wavesplatform.events.BlockchainUpdates"

inConfig(Compile)(
  Seq(
    PB.protoSources in Compile := Seq(PB.externalIncludePath.value),
    includeFilter in PB.generate := new SimpleFileFilter((f: File) => f.getName.endsWith(".proto") && f.getParent.replace('\\', '/').endsWith("waves/events")),
    PB.targets += scalapb.gen(flatPackage = true) -> sourceManaged.value
  ))


enablePlugins(RunApplicationSettings, WavesExtensionDockerPlugin, ExtensionPackaging)
test in assembly := {}
/*
docker := docker.dependsOn(LocalProject("node-it") / docker).value
inTask(docker)(
  Seq(
    imageNames := Seq(ImageName("com.wavesplatform/blockchain-updates")),
    exposedPorts := Set(6886),
    additionalFiles ++= Seq(
      (LocalProject("blockchain-updates") / Universal / stage).value
    )
  ))
*/

assemblyMergeStrategy in assembly := {
  case PathList("META-INF", xs @ _*) => MergeStrategy.discard
  case x => MergeStrategy.first
}
