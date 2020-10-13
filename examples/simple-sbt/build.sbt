name := "scalacheck-demo"

scalaVersion := "2.12.10"

libraryDependencies += "org.scalacheck" %% "scalacheck" % "1.14.2" % "test"

Test / testOptions += Tests.Argument(TestFrameworks.ScalaCheck, "-maxSize", "5", "-minSuccessfulTests", "33", "-workers", "1", "-verbosity", "1")
