import Dependencies._

name := "generic-bank-interview"

version := "0.1"

scalaVersion := "2.13.4"

libraryDependencies ++= Seq(
  Libraries.akkaStream,
  Libraries.cats,
  Libraries.enumeratum,
  Libraries.pureConfig,
  Libraries.logback,
  Libraries.circeCore,
  Libraries.circeParser,
  Libraries.akkaStreamTestKit       % Test,
  Libraries.quicklens               % Test,
  Libraries.scalaCheck              % Test,
  Libraries.scalaTest               % Test,
  Libraries.scalaTestPlusScalaCheck % Test,
  Libraries.circeLiteral            % Test
)

addCommandAlias("update", ";dependencyUpdates; reload plugins; dependencyUpdates; reload return")
addCommandAlias("fmt", ";scalafmtSbt;scalafmtAll")
