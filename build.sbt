onLoad in Global := ((s: State) => { "updateIdea" :: s}) compose (onLoad in Global).value

lazy val scioIdeaPlugin: Project =
  Project("scio-idea", file("."))
    .enablePlugins(SbtIdeaPlugin)
    .settings(
      name := "scio-idea",
      version := "0.1.0",
      scalaVersion := "2.11.7",
      assemblyOption in assembly := (assemblyOption in assembly).value.copy(includeScala = false),
      ideaInternalPlugins := Seq(),
      ideaExternalPlugins := Seq(IdeaPlugin.Zip("scala-plugin", url("https://plugins.jetbrains.com/files/1347/21610/scala-intellij-bin-1.9.2.zip"))),
      aggregate in updateIdea := false,
      assemblyExcludedJars in assembly <<= ideaFullJars,
      ideaBuild := "143.116.4" //IDEA 15 public preview,
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
