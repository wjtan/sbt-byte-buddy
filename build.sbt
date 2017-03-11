name := "sbt-byte-buddy"

organization := "net.bytebuddy"

version := "1.0.0"

scalaVersion := "2.10.6"

sbtPlugin := true

libraryDependencies += "net.bytebuddy" % "byte-buddy" % "1.6.9"

bintrayRepository := "maven"

bintrayPackage := "sbt-byte-buddy"

publishMavenStyle := true

licenses +=("Apache-2.0", url("http://opensource.org/licenses/apache2.0.php"))
