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

package com.spotify.scio

import java.io.File
import java.nio.charset.Charset
import java.nio.file.{Paths, Files => JFiles}

import com.google.common.base.Charsets
import com.google.common.hash.Hashing
import com.google.common.io.Files
import com.intellij.notification.{Notification, NotificationType, Notifications}
import com.intellij.openapi.diagnostic.Logger
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef.SyntheticMembersInjector

import scala.collection.mutable

object AnnotationTypeInjector {
  private val Log = Logger.getInstance(classOf[AnnotationTypeInjector])
  private val CaseClassArgs = """case\s+class\s+[^(]+\((.*)\).*""".r
  private val TypeArg = """[a-zA-Z0-9_$]+\s*:\s*[a-zA-Z0-9._$]+([\[(](.*?)[)\]]+)?""".r
  private val AlertEveryMissedXInvocations = 5

  def getApplyPropsSignature(caseClasses: Option[String]): Seq[String] =
    getConstructorProps(caseClasses)
      .map(_.props)
      .getOrElse(Seq.empty)

  def getConstructorProps(caseClasses: Option[String]): Option[ConstructorProps] =
    caseClasses.collect {
      case CaseClassArgs(params) => ConstructorProps(TypeArg.findAllIn(params).toSeq)
    }

  def getUnapplyReturnTypes(caseClasses: Option[String]): Seq[String] =
    getConstructorProps(caseClasses).map(_.types).getOrElse(Seq.empty)

  def getTupledMethod(returnClassName: String, caseClasses: Option[String]): String = {
    val maybeTupledMethod = getConstructorProps(caseClasses).map {
      case cp: ConstructorProps if (2 to 22).contains(cp.types.size) =>
        s"def tupled: _root_.scala.Function1[( ${cp.types.mkString(" , ")} ), $returnClassName ] = ???"
      case _ =>
        ""
    }

    maybeTupledMethod.getOrElse("")
  }

  final case class ConstructorProps(props: Seq[String]) {
    val types: Seq[String] = props.map(_.split(" : ")(1).trim)
  }

  /**
   * Finds BigQuery cache file, must be in sync with Scio implementation, otherwise plugin will
   * not be able to find scala files.
   */
  private def file(filename: String): Option[File] = {
    val sysPropOverride =
      Seq("generated.class.cache.directory", "bigquery.class.cache.directory")
        .flatMap(sys.props.get)
        .headOption
        .map(Paths.get(_).resolve(filename))

    val resolved = sysPropOverride.getOrElse {
      val oldBqPath = Paths
        .get(sys.props("java.io.tmpdir"))
        .resolve("bigquery-classes")
        .resolve(filename)
      val newBqPath = Paths
        .get(sys.props("java.io.tmpdir"))
        .resolve(sys.props("user.name"))
        .resolve("bigquery-classes")
        .resolve(filename)
      val path = Paths
        .get(sys.props("java.io.tmpdir"))
        .resolve(sys.props("user.name"))
        .resolve("generated-classes")
        .resolve(filename)

      Seq(path, newBqPath, oldBqPath).find(JFiles.exists(_)).getOrElse(path)
    }

    val file = resolved.toFile
    if (file.exists()) {
      Log.debug(s"Found $resolved")
      Some(file)
    } else {
      Log.warn(s"missing file: $resolved")
      None
    }
  }

  /**
   * Computes hash for macro - the hash must be consistent with hash implementation in Scio.
   */
  def hash(owner: String, srcFile: String): String =
    Hashing
      .murmur3_32()
      .newHasher()
      .putString(owner, Charsets.UTF_8)
      .putString(srcFile, Charsets.UTF_8)
      .hash()
      .toString
}

trait AnnotationTypeInjector extends SyntheticMembersInjector {
  import AnnotationTypeInjector._

  private[this] val classMissed =
    mutable.HashMap.empty[String, Int].withDefaultValue(0)

  protected def generatedCaseClasses(source: String, c: ScClass) = {
    // For some reason sometimes [[getVirtualFile]] returns null, use Option. I don't know why.
    val fileName =
      Option(c.asInstanceOf[PsiElement].getContainingFile.getVirtualFile)
      // wrap VirtualFile to java.io.File to use OS file separator
        .map(vf => new File(vf.getCanonicalPath).getCanonicalPath)

    val file = for {
      psiElementPath <- fileName
      hash <- Some(hash(source, psiElementPath))
      cf <- classFile(c, hash)
    } yield cf

    file.fold(Seq.empty[String]) { f =>
      import collection.JavaConverters._
      Files
        .readLines(f, Charset.defaultCharset())
        .asScala
        .filter(_.contains("case class"))
    }
  }

  protected def classFile(klass: ScClass, hash: String): Option[java.io.File] = {
    val filename = s"${klass.name}-$hash.scala"
    file(filename) match {
      case f: Some[File] =>
        classMissed(filename) = 0
        f
      case none =>
        classMissed(filename) += 1
        val errorMessage =
          "Scio plugin could not find scala files for code completion. Please (re)compile the project."
        if (classMissed(filename) >= AlertEveryMissedXInvocations) {
          // reset counter
          classMissed(filename) = 0
          val notification = new Notification(
            "ScioIDEA",
            "Scio Plugin",
            errorMessage,
            NotificationType.ERROR
          )
          Notifications.Bus.notify(notification)
        }
        none
    }
  }
}
