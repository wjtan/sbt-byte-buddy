name := "enhantment-test"

scalaVersion := "2.12.7"

resolvers ++= DefaultOptions.resolvers(snapshot = true)

scalaSource in Compile := baseDirectory.value / "src"

libraryDependencies += "net.bytebuddy" % "byte-buddy" % "1.9.4"

byteBuddyPlugins := Seq("net.bytebuddy.test.TestPlugin")
