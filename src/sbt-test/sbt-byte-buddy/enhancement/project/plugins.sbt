resolvers ++= DefaultOptions.resolvers(snapshot = true)

sys.props.get("plugin.version") match {
  case Some(x) => addSbtPlugin("net.bytebuddy" % "sbt-byte-buddy" % x)
  case _ => sys.error("""|The system property 'plugin.version' is not defined.
                           |Specify this property using the scriptedLaunchOpts -D.""".stripMargin)
}

//addSbtPlugin("net.bytebuddy" % "sbt-byte-buddy" % "1.0.0-SNAPSHOT")
