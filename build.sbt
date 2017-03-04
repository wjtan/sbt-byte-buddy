name := "sbt-byte-buddy"

organization := "net.bytebuddy"

version := "1.0.0-SNAPSHOT"

scalaVersion := "2.10.6"

sbtPlugin := true

libraryDependencies += "net.bytebuddy" % "byte-buddy" % "1.6.9"

// licenses +=("Apache-2.0", url("http://opensource.org/licenses/apache2.0.php"))

// enable scala code formatting //
//import scalariform.formatter.preferences._
//import com.typesafe.sbt.SbtScalariform

// Scalariform settings
//SbtScalariform.autoImport.scalariformPreferences := SbtScalariform.autoImport.scalariformPreferences.value
//  .setPreference(AlignSingleLineCaseStatements, true)
//  .setPreference(AlignSingleLineCaseStatements.MaxArrowIndent, 100)
//  .setPreference(DoubleIndentClassDeclaration, true)

// enable updating file headers //
//import de.heikoseeberger.sbtheader.license.Apache2_0

//headers := Map(
//  "scala" -> Apache2_0("2016", "Tan Wen Jun"),
//  "conf" -> Apache2_0("2016", "Tan Wen Jun", "#")
//)

//enablePlugins(AutomateHeaderPlugin, SbtScalariform)

