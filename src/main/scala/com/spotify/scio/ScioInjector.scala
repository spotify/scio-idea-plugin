/*
 * Copyright 2016 Spotify AB.
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
import java.nio.file.{Path, Paths, Files => JFiles}

import com.google.common.base.Charsets
import com.google.common.hash.Hashing
import com.google.common.io.Files
import com.intellij.openapi.diagnostic.Logger
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef.SyntheticMembersInjector

import scala.collection.mutable

class ScioInjector extends SyntheticMembersInjector {
  private val logger = Logger.getInstance(classOf[ScioInjector])

  // Could not find a way to get fully qualified annotation names
  // even tho there is API, it does not return the annotations.
  // For now stick with relative annotation names.
  private val BQTNamespace = "BigQueryType"
  private val fromQuery = s"$BQTNamespace.fromQuery"
  private val fromTable = s"$BQTNamespace.fromTable"
  private val annotations = Seq(fromQuery,
                                fromTable,
                                s"$BQTNamespace.fromSchema",
                                s"$BQTNamespace.toTable")

  private val AvroTNamespace = "AvroType"
  private val avroAnnotations = Seq(s"$AvroTNamespace.fromSchema",
                                    s"$AvroTNamespace.fromPath",
                                    s"$AvroTNamespace.toSchema")

  private val alertEveryMissedXInvocations = 5
  private val classMissed = mutable.HashMap.empty[String, Int].withDefaultValue(0)

  /**
   * Finds BigQuery cache file, must be in sync with Scio implementation, otherwise plugin will
   * not be able to find scala files.
   */

  private def getBQClassCacheFile(filename: String): Path = {
    //TODO: add this as key/value settings with default etc
    if (sys.props("bigquery.class.cache.directory") != null) {
      Paths.get(sys.props("bigquery.class.cache.directory"))
        .resolve(filename)
    } else {
      val oldPath = Paths.get(sys.props("java.io.tmpdir"))
        .resolve("bigquery-classes")
        .resolve(filename)
      val newPath = Paths.get(sys.props("java.io.tmpdir"))
        .resolve(sys.props("user.name"))
        .resolve("bigquery-classes")
        .resolve(filename)
      // use old path only if it's the only one available
      if (JFiles.exists(oldPath) && !JFiles.exists(newPath)) oldPath else newPath
    }
  }

  private def findClassFile(fileName: String): Option[java.io.File] = {
    val classFile = getBQClassCacheFile(fileName).toFile
    val classFilePath = classFile.getAbsolutePath
    if (classFile.exists()) {
      logger.debug(s"Found $classFilePath")
      classMissed(fileName) = 0
      Some(classFile)
    } else {
      classMissed(fileName) += 1
      val errorMessage = s"""|Scio plugin could not find scala files for code completion. Please (re)compile the project.
                             |Missing: $classFilePath""".stripMargin
      if(classMissed(fileName) >= alertEveryMissedXInvocations) {
        // reset counter
        classMissed(fileName) = 0
        logger.error(errorMessage)
      }
      logger.warn(errorMessage)
      None
    }
  }

  /**
   * Computes hash for macro - the hash must be consistent with hash implementation in Scio.
   */
  private def genHashForMacro(owner: String, srcFile: String): String = {
    Hashing.murmur3_32().newHasher()
      .putString(owner, Charsets.UTF_8)
      .putString(srcFile, Charsets.UTF_8)
      .hash().toString
  }

  /**
   * Main method of the plugin. Injects syntactic inner members like case classes and companion
   * objects, makes IntelliJ happy about BigQuery macros. Assumes macro is enclosed within
   * class/object.
   */
  override def injectInners(source: ScTypeDefinition): Seq[String] = {
    source.members.flatMap {
      case c: ScClass if c.annotations.map(_.getText).exists(t => annotations.exists(t.contains)) =>
        val caseClasses = fetchGeneratedCaseClasses(source, c)
        val extraCompanionMethod = fetchExtraBQTypeCompanionMethods(source, c)
        val tupledMethod = getTupledMethod(c.getName, caseClasses)

        val applyPropsSignature = getApplyPropsSignature(caseClasses)
        val unapplyReturnTypes = getUnapplyReturnTypes(caseClasses).mkString(" , ")

        // TODO: missing extends and traits - are they needed?
        // $tn extends ${p(c, SType)}.HasSchema[$name] with ..$traits
        val companion = s"""|object ${c.getName} {
                            |  def apply( $applyPropsSignature ): ${c.getName} = ???
                            |  def unapply(x$$0: ${c.getName}): _root_.scala.Option[($unapplyReturnTypes)] = ???
                            |  def fromTableRow: _root_.scala.Function1[_root_.com.google.api.services.bigquery.model.TableRow, ${c.getName} ] = ???
                            |  def toTableRow: _root_.scala.Function1[ ${c.getName}, _root_.com.google.api.services.bigquery.model.TableRow] = ???
                            |  def schema: _root_.com.google.api.services.bigquery.model.TableSchema = ???
                            |  def toPrettyString(indent: Int = 0): String = ???
                            |  $extraCompanionMethod
                            |  $tupledMethod
                            |}""".stripMargin

        if (caseClasses.isEmpty) {
          Seq.empty
        } else {
          caseClasses ++ Seq(companion)
        }

      case c: ScClass if c.annotations.map(_.getText).exists(t => avroAnnotations.exists(t.contains)) =>
        val caseClasses = fetchGeneratedCaseClasses(source, c)
        val tupledMethod = getTupledMethod(c.getName, caseClasses)
        val applyPropsSignature = getApplyPropsSignature(caseClasses)
        val unapplyReturnTypes = getUnapplyReturnTypes(caseClasses).mkString(" , ")

        val companion = s"""|object ${c.getName} {
                            |  def apply( $applyPropsSignature ): ${c.getName} = ???
                            |  def unapply(x$$0: ${c.getName}): _root_.scala.Option[($unapplyReturnTypes)] = ???
                            |  def fromGenericRecord: _root_.scala.Function1[_root_.org.apache.avro.generic.GenericRecord, ${c.getName} ] = ???
                            |  def toGenericRecord: _root_.scala.Function1[ ${c.getName}, _root_.org.apache.avro.generic.GenericRecord] = ???
                            |  def schema: _root_.org.apache.avro.Schema = ???
                            |  def toPrettyString(indent: Int = 0): String = ???
                            |  $tupledMethod
                            |}""".stripMargin

        if (caseClasses.isEmpty) {
          Seq.empty
        } else {
          caseClasses ++ Seq(companion)
        }
      case _ => Seq.empty
    }
  }

  private def getApplyPropsSignature(caseClasses: Seq[String]) = {
    getConstructorProps(caseClasses).map(_.props)
      .getOrElse(Seq.empty)
      .mkString(" , ")
  }

  private def fetchExtraBQTypeCompanionMethods(source: ScTypeDefinition, c: ScClass) = {
    val annotation = c.annotations.map(_.getText).find(t => annotations.exists(t.contains)).get
    logger.debug(s"Found $annotation in ${source.getTruncedQualifiedName}")

    val extraCompanionMethod = annotation match {
      case a if a.contains(fromQuery) => "def query: _root_.java.lang.String = ???"
      case a if a.contains(fromTable) => "def table: _root_.java.lang.String = ???"
      case _ => ""
    }
    extraCompanionMethod
  }

  private def fetchGeneratedCaseClasses(source: ScTypeDefinition, c: ScClass) = {
    // For some reason sometimes [[getVirtualFile]] returns null, use Option. I don't know why.
    val fileName = Option(c.asInstanceOf[PsiElement].getContainingFile.getVirtualFile)
      // wrap VirtualFile to java.io.File to use OS file separator
      .map(vf => new File(vf.getCanonicalPath).getCanonicalPath)

    val hash = fileName.map(genHashForMacro(source.getTruncedQualifiedName, _))

    hash.flatMap(h => findClassFile(s"${c.getName}-$h.scala")).map(f => {
      import collection.JavaConverters._
      Files.readLines(f, Charset.defaultCharset()).asScala.filter(_.contains("case class"))
    }).getOrElse(Seq.empty)
  }

  private def getConstructorProps(caseClasses: Seq[String]): Option[ConstructorProps] = {
    // TODO: duh. who needs regex ... but seriously tho, should this be regex?
    caseClasses
      .find(c =>
        c.contains("extends _root_.com.spotify.scio.bigquery.types.BigQueryType.HasAnnotation") ||
          c.contains("extends _root_.com.spotify.scio.avro.types.AvroType.HasAvroAnnotation"))
      .map(
        _.split("[()]").filter(_.contains(" : "))  // get only parameter part
          .flatMap(propsStr => {
            val propsSplit = propsStr.split(",")
            // We need to fix the split since Map types contain ',' as a part of their type declaration
            val props = mutable.ArrayStack[String]()
            for (prop <- propsSplit) {
              if (prop.contains(" : ")) {
                props += prop
              } else {
                assume(props.nonEmpty)
                props += props.pop() + "," + prop
              }
            }
            props.result.toList
        })).map(ConstructorProps(_)) // get individual parameter
  }

  private[scio] def getUnapplyReturnTypes(caseClasses: Seq[String]): Seq[String] = {
    getConstructorProps(caseClasses).map(_.types).getOrElse(Seq.empty)
  }

  private[scio] def getTupledMethod(returnClassName: String, caseClasses: Seq[String]): String = {
    val maybeTupledMethod = getConstructorProps(caseClasses).map {
      case cp: ConstructorProps if (2 to 22).contains(cp.types.size) =>
        s"def tupled: _root_.scala.Function1[( ${cp.types.mkString(" , ")} ), $returnClassName ] = ???"
      case _ =>
        ""
    }
    maybeTupledMethod.getOrElse("")
  }

  case class ConstructorProps(props: Seq[String]) {
    val types: Seq[String] = props.map(_.split(" : ")(1).trim)
  }
}
