/*
 * Copyright 2019 Spotify AB.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

lazy val Guava = "com.google.guava" % "guava" % "23.0"
lazy val Scalatest = "org.scalatest" %% "scalatest" % "3.0.8"

lazy val commonSettings = Def.settings(
  scalaVersion := "2.12.10",
  scalacOptions ++= Seq(
    "-deprecation",
    "-encoding",
    "utf-8",
    "-explaintypes",
    "-feature",
    "-Xcheckinit",
    "-Xfatal-warnings",
    "-Xfuture",
    "-Xlint:adapted-args",
    "-Xlint:by-name-right-associative",
    "-Xlint:constant",
    "-Xlint:delayedinit-select",
    "-Xlint:doc-detached",
    "-Xlint:inaccessible",
    "-Xlint:infer-any",
    "-Xlint:missing-interpolator",
    "-Xlint:nullary-override",
    "-Xlint:nullary-unit",
    "-Xlint:option-implicit",
    "-Xlint:package-object-classes",
    "-Xlint:poly-implicit-overload",
    "-Xlint:private-shadow",
    "-Xlint:stars-align",
    "-Xlint:type-parameter-shadow",
    "-Xlint:unsound-match",
    "-Yno-adapted-args"
  )
)

lazy val ideaSettings = Def.settings(
  ThisBuild / intellijPluginName := "scio-idea",
  ThisBuild / intellijPlatform := IntelliJPlatform.IdeaCommunity,
  ThisBuild / intellijBuild := "192.6817.14",
  intellijInternalPlugins := Seq("java"),
  intellijExternalPlugins += "org.intellij.scala".toPlugin
)

lazy val packagingSettings = Def.settings(
  packageMethod := PackagingMethod.Standalone()
)

lazy val scioIdeaPlugin: Project = project
  .in(file("."))
  .settings(commonSettings)
  .settings(ideaSettings)
  .settings(packagingSettings)
  .settings(
    name := "scio-idea",
    libraryDependencies ++= Seq(
      Guava,
      Scalatest % Test
    ),
    // workaround for scala-library link error
    // https://intellij-support.jetbrains.com/hc/en-us/community/posts/360005023760-LinkageError-related-to-Scala-collections-after-upgrade-to-2019-2
    packageLibraryMappings ++= Seq(
      "org.scala-lang" % "scala-.*" % ".*" -> None,
      "org.scala-lang.modules" % "scala-.*" % ".*" -> None
    )
  )
  .enablePlugins(SbtIdeaPlugin)

lazy val ideaRunner: Project =
  createRunnerProject(scioIdeaPlugin, "idea-runner")
