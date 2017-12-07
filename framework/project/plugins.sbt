import sbt.Defaults.sbtPluginExtra
// Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>

enablePlugins(BuildInfoPlugin)

val Versions = new {
  // when updating sbtNativePackager version, be sure to also update the documentation links in
  // documentation/manual/working/commonGuide/production/Deploying.md
  val sbtNativePackager = "1.3.2"
  val mima = "0.1.18"
  val sbtScalariform = "1.8.2"
  val sbtJavaAgent = "0.1.4"
  val sbtJmh = "0.2.27"
  val sbtDoge = "0.1.5"
  val webjarsLocatorCore = "0.33"
  val sbtHeader = "4.0.0"
  val sbtTwirl: String = sys.props.getOrElse("twirl.version", "1.3.12")
  val interplay: String = sys.props.getOrElse("interplay.version", "1.3.14-SNAPSHOT")
}

buildInfoKeys := Seq[BuildInfoKey](
  "sbtNativePackagerVersion" -> Versions.sbtNativePackager,
  "sbtTwirlVersion" -> Versions.sbtTwirl,
  "sbtJavaAgentVersion" -> Versions.sbtJavaAgent
)

logLevel := Level.Warn

scalacOptions ++= Seq("-deprecation", "-language:_")

addSbtPlugin("com.typesafe.play" % "interplay" % Versions.interplay)
addSbtPlugin("com.typesafe.sbt" % "sbt-twirl" % Versions.sbtTwirl)
addSbtPlugin("com.typesafe" % "sbt-mima-plugin" % Versions.mima)
addSbtPlugin("org.scalariform" % "sbt-scalariform" % Versions.sbtScalariform)
addSbtPlugin("com.lightbend.sbt" % "sbt-javaagent" % Versions.sbtJavaAgent)
addSbtPlugin("pl.project13.scala" % "sbt-jmh" % Versions.sbtJmh)
addSbtPlugin("de.heikoseeberger" % "sbt-header" % Versions.sbtHeader)

libraryDependencies ++= {
  // Add different plugins according to the version of sbt we're using.
  val sbtV = (sbtBinaryVersion in update).value
  val scalaV = (scalaBinaryVersion in update).value
  if (sbtV.startsWith("0.")) {
    Seq(
      "org.scala-sbt" % "scripted-plugin" % sbtVersion.value,
      sbtPluginExtra("com.eed3si9n" % "sbt-doge" % Versions.sbtDoge, sbtV, scalaV)
    )
  } else {
    Seq(
      "org.scala-sbt" %% "scripted-plugin" % sbtVersion.value
    )
  }
}

libraryDependencies += "org.webjars" % "webjars-locator-core" % Versions.webjarsLocatorCore

resolvers += Resolver.typesafeRepo("releases")