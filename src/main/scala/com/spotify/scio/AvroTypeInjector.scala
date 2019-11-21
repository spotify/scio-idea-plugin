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

object AvroTypeInjector {
  private val Log = Logger.getInstance(classOf[AvroTypeInjector])

  private val AvroTNamespace = "AvroType"
  private val AvroAnnotations = Seq(
    s"$AvroTNamespace.fromSchema",
    s"$AvroTNamespace.fromPath",
    s"$AvroTNamespace.toSchema"
  )
  private val CaseClassSuper =
    "_root_.com.spotify.scio.avro.types.AvroType.HasAvroAnnotation"

  private def avroAnnotation(sc: ScClass): Option[String] =
    sc.annotations
      .map(_.getText)
      .find(t => AvroAnnotations.exists(t.contains))
}

final class AvroTypeInjector extends AnnotationTypeInjector {
  import AvroTypeInjector._
  import AnnotationTypeInjector._

  override def needsCompanionObject(source: ScTypeDefinition): Boolean = false

  override def injectFunctions(source: ScTypeDefinition): Seq[String] =
    source match {
      case c: ScClass if avroAnnotation(c).isDefined =>
        val parent = c.containingClass.getQualifiedName.init
        val caseClasses = generatedCaseClasses(parent, c).find { c =>
          c.contains(CaseClassSuper)
        }
        getApplyPropsSignature(caseClasses).map(v => s"def $v = ???")
      case _ => Seq.empty
    }

  override def injectSupers(source: ScTypeDefinition): Seq[String] =
    source match {
      case c: ScClass if avroAnnotation(c).isDefined => Seq(CaseClassSuper)
      case _                                         => Seq.empty
    }

  /**
   * Main method of the plugin. Injects syntactic inner members like case classes and companion
   * objects, makes IntelliJ happy about BigQuery macros. Assumes macro is enclosed within
   * class/object.
   */
  override def injectInners(source: ScTypeDefinition): Seq[String] = {
    source.extendsBlock.members
      .collect {
        case c: ScClass if avroAnnotation(c).isDefined =>
          val caseClasses =
            generatedCaseClasses(source.getQualifiedName.init, c).find { c =>
              c.contains(CaseClassSuper)
            }
          (c, caseClasses)
      }
      .collect {
        case (c, caseClasses) if caseClasses.nonEmpty =>
          val tupledMethod = getTupledMethod(c.getName, caseClasses)
          val applyPropsSignature =
            getApplyPropsSignature(caseClasses).mkString(",")
          val unapplyReturnTypes =
            getUnapplyReturnTypes(caseClasses).mkString(",")

          s"""|object ${c.getName} {
              |  def apply( $applyPropsSignature ): ${c.getName} = ???
              |  def unapply(x$$0: ${c.getName}): _root_.scala.Option[($unapplyReturnTypes)] = ???
              |  def fromGenericRecord: _root_.scala.Function1[_root_.org.apache.avro.generic.GenericRecord, ${c.getName} ] = ???
              |  def toGenericRecord: _root_.scala.Function1[ ${c.getName}, _root_.org.apache.avro.generic.GenericRecord] = ???
              |  def schema: _root_.org.apache.avro.Schema = ???
              |  def toPrettyString(indent: Int = 0): String = ???
              |  $tupledMethod
              |}""".stripMargin
      }
  }
}
