name := "TestByteBuddyPlugin"

version := "1.0"

scalaVersion := "2.12.1"

javaSource in Compile := baseDirectory.value / "src"

libraryDependencies += "net.bytebuddy" % "byte-buddy" % "1.6.9"