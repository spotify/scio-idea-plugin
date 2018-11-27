/*
 * Copyright 2017 Spotify AB.
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
lazy val Scalatest = "org.scalatest" %% "scalatest" % "3.0.5"

lazy val commonSettings = Def.settings(
  scalaVersion := "2.12.7"
)

lazy val scioIdeaPlugin: Project = project
  .in(file("."))
  .settings(commonSettings)
  .settings(
    name := "scio-idea",
    version := "0.1.12",
    assemblyOption in assembly := (assemblyOption in assembly).value
      .copy(includeScala = false),
    ideaInternalPlugins := Seq(),
    ideaExternalPlugins := Seq(
      IdeaPlugin.Zip(
        "scala-plugin",
        url("https://plugins.jetbrains.com/plugin/download?updateId=41257"))),
    aggregate in updateIdea := false,
    assemblyExcludedJars in assembly := ideaFullJars.value,
    ideaBuild := "181.5540.7",
    libraryDependencies ++= Seq(
      Guava,
      Scalatest % Test
    )
  )
  .enablePlugins(SbtIdeaPlugin)

lazy val ideaRunner: Project =
  createRunnerProject(scioIdeaPlugin, "idea-runner")
