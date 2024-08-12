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

disablePlugins(TypelevelCiSigningPlugin)

lazy val Guava = "com.google.guava" % "guava" % "33.2.1-jre"
lazy val Scalatest = "org.scalatest" %% "scalatest" % "3.2.19"

// idea settings
lazy val intelliJVersion = "2022.3.1"
ThisBuild / intellijPluginName := "scio-idea"
ThisBuild / intellijPlatform := IntelliJPlatform.IdeaCommunity
ThisBuild / intellijBuild := intelliJVersion

// project
ThisBuild / tlBaseVersion := "0.1"
ThisBuild / scalaVersion := "2.13.14"
ThisBuild / githubWorkflowTargetBranches := Seq("main")
ThisBuild / githubWorkflowJavaVersions := Seq(JavaSpec.corretto("17"))
ThisBuild / tlJdkRelease := Some(17)
ThisBuild / tlFatalWarnings := true
ThisBuild / tlCiHeaderCheck := true
ThisBuild / tlCiScalafmtCheck := true
ThisBuild / tlCiDocCheck := false
ThisBuild / tlCiMimaBinaryIssueCheck := false
ThisBuild / tlCiDependencyGraphJob := false
ThisBuild / githubWorkflowBuild := Seq(
  WorkflowStep.Sbt(
    name = Some("Test"),
    commands = List("test", "runPluginVerifier", "packageArtifact")
  )
)
ThisBuild / githubWorkflowPublish := Seq(
  WorkflowStep.Sbt(
    name = Some("Publish"),
    commands = List("packageArtifactZip", "publishPlugin"),
    env = Map("IJ_PLUGIN_REPO_TOKEN" -> "${{ secrets.IJ_PLUGIN_TOKEN }}")
  )
)
val cache = UseRef.Public("actions", "cache", "v4")
ThisBuild / githubWorkflowGeneratedCacheSteps := Seq(
  WorkflowStep.Use(
    name = Some("Cache"),
    ref = cache,
    params = Map(
      "path" -> "~/.scio-ideaPluginIC",
      "key" -> s"idea-$intelliJVersion"
    )
  )
)

lazy val scioIdeaPlugin: Project = project
  .in(file("."))
  .enablePlugins(SbtIdeaPlugin)
  .settings(
    libraryDependencies ++= Seq(
      Guava,
      Scalatest % Test
    ),
    intellijPlugins += "org.intellij.scala".toPlugin,
    patchPluginXml := pluginXmlOptions { xml =>
      xml.version = version.value
      // https://plugins.jetbrains.com/docs/intellij/build-number-ranges.html#platformVersions
      xml.sinceBuild = "223" // for 2022.3
    },
    // verify against latest IntelliJ IDEA Community
    pluginVerifierOptions := pluginVerifierOptions.value.copy(
      overrideIDEs = Seq(
        intellijBaseDirectory.value.toString,
        "[latest-IC]"
      )
    )
  )
