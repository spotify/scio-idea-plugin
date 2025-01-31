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
import org.jetbrains.sbtidea.verifier.FailureLevel
import scala.jdk.CollectionConverters._

disablePlugins(TypelevelCiSigningPlugin)

lazy val Guava = "com.google.guava" % "guava" % "33.4.0-jre"
lazy val Scalatest = "org.scalatest" %% "scalatest" % "3.2.19"

// idea settings
// https://plugins.jetbrains.com/docs/intellij/build-number-ranges.html#platformVersions
lazy val intellijBranchNumber = "242.2"
// https://www.jetbrains.com/idea/download/other.html
ThisBuild / intellijBuild := "242.20224.300"
ThisBuild / intellijPluginName := "scio-idea"
ThisBuild / intellijPlatform := IntelliJPlatform.IdeaCommunity

// project
ThisBuild / tlBaseVersion := "0.1"
ThisBuild / scalaVersion := "2.13.16"
ThisBuild / githubWorkflowTargetBranches := Seq("main")
ThisBuild / githubWorkflowJavaVersions := Seq(JavaSpec.corretto("21"))
ThisBuild / tlJdkRelease := Some(21)
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
      "key" -> s"idea-$intellijBranchNumber"
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
      xml.sinceBuild = intellijBuild.value
    },
    pluginVerifierOptions := pluginVerifierOptions.value.copy(
      // verify against latest IntelliJ IDEA Community
      overrideIDEs = Seq(
        intellijBaseDirectory.value.toString,
        "[latest-release-IC]"
      ),
      // allow experimental API usages
      failureLevels = FailureLevel.ALL.asScala
        .filter(_ != FailureLevel.EXPERIMENTAL_API_USAGES)
        .toSet
    )
  )
