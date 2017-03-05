name := "enhantment-test"

scalaVersion := "2.12.1"

resolvers ++= DefaultOptions.resolvers(snapshot = true)

scalaSource in Compile := baseDirectory.value / "src"

libraryDependencies += "net.bytebuddy" % "byte-buddy" % "1.6.9"

byteBuddyPlugins := Seq("net.bytebuddy.test.TestPlugin")
