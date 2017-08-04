onLoad in Global := ((s: State) => { "updateIdea" :: s}) compose (onLoad in Global).value

lazy val scioIdeaPlugin: Project =
  Project("scio-idea", file("."))
    .enablePlugins(SbtIdeaPlugin)
    .settings(
      name := "scio-idea",
      version := "0.1.9",
      scalaVersion := "2.11.7",
      assemblyOption in assembly := (assemblyOption in assembly).value.copy(includeScala = false),
      ideaInternalPlugins := Seq(),
      ideaExternalPlugins := Seq(IdeaPlugin.Zip("scala-plugin", url("https://plugins.jetbrains.com/plugin/download?pr=&updateId=30351"))),
      aggregate in updateIdea := false,
      assemblyExcludedJars in assembly <<= ideaFullJars,
      ideaBuild := "163.7743.44",
      libraryDependencies ++= Seq(
        "com.google.guava" % "guava" % "19.0",
        "org.scalatest" %% "scalatest" % "3.0.3" % "test"
      )
    )

lazy val ideaRunner: Project = project.in(file("ideaRunner"))
  .dependsOn(scioIdeaPlugin % Provided)
  .settings(
    name := "ideaRunner",
    version := "1.0",
    scalaVersion := "2.11.7",
    autoScalaLibrary := false,
    unmanagedJars in Compile <<= ideaMainJars.in(scioIdeaPlugin),
    unmanagedJars in Compile += file(System.getProperty("java.home")).getParentFile / "lib" / "tools.jar"
  )

lazy val packagePlugin = TaskKey[File]("package-plugin", "Create plugin's zip file ready to load into IDEA")

packagePlugin in scioIdeaPlugin <<= (assembly in scioIdeaPlugin,
  target in scioIdeaPlugin,
  ivyPaths) map { (ideaJar, target, paths) =>
  val pluginName = "scio-idea"
  val ivyLocal = paths.ivyHome.getOrElse(file(System.getProperty("user.home")) / ".ivy2") / "local"
  val sources = Seq(
    ideaJar -> s"$pluginName/lib/${ideaJar.getName}"
  )
  val out = target / s"$pluginName-plugin.zip"
  IO.zip(sources, out)
  out
}
