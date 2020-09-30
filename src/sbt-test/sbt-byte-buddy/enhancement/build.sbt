name := "enhantment-test"

scalaVersion := "2.13.3"

resolvers ++= DefaultOptions.resolvers(snapshot = true)

scalaSource in Compile := baseDirectory.value / "src"

libraryDependencies += "net.bytebuddy" % "byte-buddy" % "1.10.16"

byteBuddyPlugins := Seq("net.bytebuddy.test.TestPlugin")
