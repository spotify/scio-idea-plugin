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

lazy val Guava = "com.google.guava" % "guava" % "32.1.3-jre"
lazy val Scalatest = "org.scalatest" %% "scalatest" % "3.2.18"


// project
ThisBuild / tlBaseVersion := "0.1"
ThisBuild / scalaVersion := "2.13.12"
ThisBuild / githubWorkflowTargetBranches := Seq("main")
ThisBuild / githubWorkflowJavaVersions := Seq(JavaSpec.corretto("11"))
ThisBuild / tlJdkRelease := Some(8)
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
  ),
)
ThisBuild / githubWorkflowPublish := Seq(
  WorkflowStep.Sbt(
    name = Some("Publish"),
    commands = List("packageArtifactZip", "publishPlugin"),
    env = Map("IJ_PLUGIN_REPO_TOKEN" -> "${{ secrets.IJ_PLUGIN_TOKEN }}")
  )
)

// idea settings
ThisBuild / intellijPluginName := "scio-idea"
ThisBuild / intellijPlatform := IntelliJPlatform.IdeaCommunity
ThisBuild / intellijBuild := "232.10072.27"

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
    }
  )
