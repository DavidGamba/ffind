import com.github.retronym.SbtOneJar._

name := "ffind"

version := "0.1"

scalaVersion:= "2.11.0"

resolvers += "Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/"

oneJarSettings

libraryDependencies += "commons-lang" % "commons-lang" % "2.6"

// libraryDependencies += groupID % artifactID % revision % configuration

// libraryDependencies += "log4j" % "log4j" % "1.2.17"

// libraryDependencies += "com.typesafe.scala-logging" %% "scala-logging-slf4j" % "2.1.2"

libraryDependencies += "org.slf4j" % "slf4j-api" % "1.7.7"

libraryDependencies += "ch.qos.logback" % "logback-classic" % "1.1.2"
