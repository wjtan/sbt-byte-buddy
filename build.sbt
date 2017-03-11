name := "sbt-byte-buddy"

organization := "net.bytebuddy"

version := "1.0.0"

scalaVersion := "2.10.6"

sbtPlugin := true

libraryDependencies += "net.bytebuddy" % "byte-buddy" % "1.6.9"

bintrayRepository := "sbt-plugins"

bintrayPackage := "sbt-byte-buddy"

licenses +=("Apache-2.0", url("http://www.apache.org/licenses/LICENSE-2.0.html"))
