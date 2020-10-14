name := "ScalaMedianFIlter"

version := "0.1"

scalaVersion := "2.13.3"

val AkkaVersion = "2.6.9"
libraryDependencies += "com.typesafe.akka" %% "akka-actor" % AkkaVersion
libraryDependencies += "org.scala-lang.modules" %% "scala-parallel-collections" % "0.2.0"
