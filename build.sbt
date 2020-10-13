sourceDirectory := file("dummy source directory")

val scalaMajorVersion = SettingKey[Int]("scalaMajorVersion")

scalaVersionSettings

lazy val versionNumber = "1.15.0"

def env(name: String): Option[String] =
  Option(System.getenv(name))

val isRelease = env("IS_RELEASE").exists(_ == "true")

lazy val travisCommit = env("TRAVIS_COMMIT")

lazy val scalaVersionSettings = Seq(
  scalaVersion := "2.13.3",
  crossScalaVersions := Seq("2.11.12", "2.12.10", scalaVersion.value),
  scalaMajorVersion := {
    val v = scalaVersion.value
    CrossVersion.partialVersion(v).map(_._2.toInt).getOrElse {
      throw new RuntimeException(s"could not get Scala major version from $v")
    }
  }
)

lazy val scalaJSVersion =
  env("SCALAJS_VERSION").getOrElse("1.1.1")

lazy val sharedSettings = MimaSettings.settings ++ scalaVersionSettings ++ Seq(

  name := "scalacheck",

  version := {
    val suffix =
      if (isRelease) ""
      else travisCommit.map("-" + _.take(7)).getOrElse("") + "-SNAPSHOT"
    versionNumber + suffix
  },

  isSnapshot := !isRelease,

  organization := "org.scalacheck",

  licenses := Seq("BSD 3-clause" -> url("https://opensource.org/licenses/BSD-3-Clause")),

  homepage := Some(url("http://www.scalacheck.org")),

  credentials ++= (for {
    username <- env("SONATYPE_USERNAME")
    password <- env("SONATYPE_PASSWORD")
  } yield Credentials(
    "Sonatype Nexus Repository Manager",
    "oss.sonatype.org",
    username, password
  )).toSeq,

  Compile / unmanagedSourceDirectories += (LocalRootProject / baseDirectory).value / "src" / "main" / "scala",

  Compile / packageSrc / mappings ++= (Compile / managedSources).value.map{ f =>
    // to merge generated sources into sources.jar as well
    (f, f.relativeTo((Compile / sourceManaged).value).get.getPath)
  },

  Compile / sourceGenerators += task {
    val dir = (Compile / sourceManaged).value / "org" / "scalacheck"
    codegen.genAll.map { s =>
      val f = dir / s.name
      IO.write(f, s.code)
      f
    }
  },

  Compile / unmanagedSourceDirectories += {
    val s = if (scalaMajorVersion.value >= 13 || isDotty.value) "+" else "-"
    (LocalRootProject / baseDirectory).value / "src" / "main" / s"scala-2.13$s"
  },

  Test / unmanagedSourceDirectories += (LocalRootProject / baseDirectory).value / "src" / "test" / "scala",

  resolvers += "sonatype" at "https://oss.sonatype.org/content/repositories/releases",

  // 2.11 - 2.13
  scalacOptions ++= {
    def mk(r: Range)(strs: String*): Int => Seq[String] =
      (n: Int) => if (r.contains(n)) strs else Seq.empty

    val groups: Seq[Int => Seq[String]] = Seq(
      mk(11 to 11)("-Xlint"),
      mk(11 to 12)("-Ywarn-inaccessible", "-Ywarn-nullary-override",
        "-Ywarn-nullary-unit", "-Xfuture", "-Xfatal-warnings", "-deprecation",
        "-Ywarn-infer-any", "-Ywarn-unused-import"),
      mk(11 to 13)("-encoding", "UTF-8", "-feature", "-unchecked",
        "-Ywarn-dead-code", "-Ywarn-numeric-widen"),
      mk(12 to 13)("-Xlint:-unused",
        "-Ywarn-unused:-patvars,-implicits,-locals,-privates,-explicits"))

    val n = scalaMajorVersion.value
    if (isDotty.value)
      Seq("-language:Scala2")
    else
      groups.flatMap(f => f(n))
  },

  // HACK: without these lines, the console is basically unusable,
  // since all imports are reported as being unused (and then become
  // fatal errors).
  Compile / console / scalacOptions ~= {_.filterNot("-Ywarn-unused-import" == _)},
  Test / console / scalacOptions := (Compile / console / scalacOptions).value,

  // don't use fatal warnings in tests
  Test / scalacOptions ~= (_ filterNot (_ == "-Xfatal-warnings")),

  mimaPreviousArtifacts := {
    // TODO: re-enable MiMa for 2.14 once there is a final version
    if (scalaMajorVersion.value == 14 || isDotty.value) Set()
    else Set("org.scalacheck" %%% "scalacheck" % "1.14.3")
  },

  /* Snapshots are published after successful merges to master.
   * Available with the following sbt snippet:
   * resolvers +=
   *   "Sonatype OSS Snapshots" at
   *   "https://oss.sonatype.org/content/repositories/snapshots",
   * libraryDependencies += "org.scalacheck" %% "scalacheck" % "1.15.0-a794907-SNAPSHOT" % "test",
   */
  publishTo := {
    val nexus = "https://oss.sonatype.org/"
    val (name, path) = if (isSnapshot.value) ("snapshots", "content/repositories/snapshots")
                       else ("releases", "service/local/staging/deploy/maven2")
    Some(name at nexus + path)
  },

  publishMavenStyle := true,

  // Travis should only publish snapshots
  publishArtifact := !(isRelease && travisCommit.isDefined),

  Test / publishArtifact := false,

  pomIncludeRepository := { _ => false },

  scmInfo := Some(
    ScmInfo(
      url("https://github.com/typelevel/scalacheck"),
      "scm:git:git@github.com:typelevel/scalacheck.git"
    )
  ),
  developers := List(
    Developer(
      id    = "rickynils",
      name  = "Rickard Nilsson",
      email = "rickynils@gmail.com",
      url   = url("https://github.com/rickynils")
    )
  )
)

lazy val js = project.in(file("js"))
  .settings(sharedSettings: _*)
  .settings(
    Global / scalaJSStage := FastOptStage,
    libraryDependencies += "org.scala-js" %% "scalajs-test-interface" % scalaJSVersion
  )
  .enablePlugins(ScalaJSPlugin)

lazy val jvm = project.in(file("jvm"))
  .settings(sharedSettings: _*)
  .settings(
    Compile / doc / sources := {
      if (isDotty.value) Seq()
      else (Compile / doc/ sources).value
    },
    crossScalaVersions += "0.27.0-RC1",
    Test / fork := {
      // Serialization issue in 2.13 and later
      scalaMajorVersion.value == 13 || isDotty.value // ==> true
      // else ==> false
    },
    libraryDependencies += "org.scala-sbt" %  "test-interface" % "1.0"
  )

lazy val native = project.in(file("native"))
  .settings(sharedSettings: _*)
  .settings(
    Compile / doc := (jvm / Compile / doc).value,
    scalaVersion := "2.11.12",
    crossScalaVersions := Seq("2.11.12"),
    // TODO: re-enable MiMa for native once published
    mimaPreviousArtifacts := Set(),
    libraryDependencies ++= Seq(
      "org.scala-native" %%% "test-interface" % nativeVersion
    )
  )
  .enablePlugins(ScalaNativePlugin)

lazy val bench = project.in(file("bench"))
  .dependsOn(jvm)
  .settings(scalaVersionSettings: _*)
  .settings(
    name := "scalacheck-bench",
    fork := true,
    publish / skip := true,
    mimaPreviousArtifacts := Set.empty,
  )
  .enablePlugins(JmhPlugin)
