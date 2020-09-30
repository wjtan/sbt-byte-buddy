lazy val root = (project in file("."))
  .enablePlugins(SbtPlugin)
  .settings(
    name := "sbt-byte-buddy",
    organization := "net.bytebuddy",
    version := "1.2.0",
    scalaVersion := "2.12.12",
    libraryDependencies += "net.bytebuddy" % "byte-buddy" % "1.10.16",
    bintrayRepository := "sbt-plugins",
    bintrayPackage := "sbt-byte-buddy",
    licenses +=("Apache-2.0", url("http://www.apache.org/licenses/LICENSE-2.0.html")),
    scriptedLaunchOpts := { scriptedLaunchOpts.value ++
      Seq("-Xmx1024M", "-Dplugin.version=" + version.value)
    },
    scriptedBufferLog := false
  )