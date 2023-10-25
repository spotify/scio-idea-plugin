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

import com.intellij.openapi.diagnostic.Logger
import org.jetbrains.plugins.scala.lang.psi.api.base.ScLiteral
import org.jetbrains.plugins.scala.lang.psi.api.base.literals.{ScBooleanLiteral, ScIntegerLiteral}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.impl.base.literals.ScIntegerLiteralImpl

object BigQueryTypeInjector {
  private val Log = Logger.getInstance(classOf[BigQueryTypeInjector])

  // Could not find a way to get fully qualified annotation names
  // even tho there is API, it does not return the annotations.
  // For now stick with relative annotation names.
  private val BQTNamespace = "BigQueryType"
  private val FromQuery = s"$BQTNamespace.fromQuery"
  private val FromTable = s"$BQTNamespace.fromTable"
  private val FromStorage = s"$BQTNamespace.fromStorage"
  private val BigQueryAnnotations = Seq(
    FromQuery,
    FromTable,
    FromStorage,
    s"$BQTNamespace.fromSchema",
    s"$BQTNamespace.toTable"
  )

  private val CaseClassSuper =
    "_root_.com.spotify.scio.bigquery.types.BigQueryType.HasAnnotation"

  private def bqAnnotation(sc: ScClass): Option[String] =
    sc.annotations
      .map(_.getText)
      .find(t => BigQueryAnnotations.exists(t.contains))

  private def bqQuerySignature(sc: ScClass): Option[String] = {
    sc.annotations.iterator
      .find(sa => sa.getText.contains(FromQuery))
      .flatMap { sa =>
        sa.annotationExpr.getAnnotationParameters.toList match {
          case Nil       => None // no args
          case _ :: Nil  => None // only a query
          case _ :: tail =>
            // query with args
            val optResult = tail
              .foldLeft(Option((0, List.empty[String]))) {
                case (res @ None, _) => res
                case (Some((idx, acc)), paramLiteral: ScLiteral) =>
                  val tpe = "_root_." + paramLiteral.getValue().getClass.getCanonicalName
                  Some((idx + 1, s"queryArg$$${idx}: ${tpe}" :: acc))
                case _ => None
              }
            optResult.map { case (_, params) => params.reverse.mkString(", ") }
        }
      }
  }

  private def fetchExtraBQTypeCompanionMethods(source: ScTypeDefinition, c: ScClass): String = {
    val annotation = bqAnnotation(c).getOrElse("")
    Log.debug(s"Found $annotation in ${source.getQualifiedNameForDebugger}")

    annotation match {
      case a if a.contains(FromQuery) =>
        val simple = """
          |def query: _root_.java.lang.String = ???
          |def queryRaw: _root_.java.lang.String = ???
          |""".stripMargin

        bqQuerySignature(c)
          .map { params =>
            simple + s"""
              |def query($params): _root_.java.lang.String = ???
              |def queryAsSource($params): _root_.com.spotify.scio.bigquery.Query = ???
              |""".stripMargin
          }
          .getOrElse(simple)
      case a if a.contains(FromTable) =>
        "def table: _root_.java.lang.String = ???"
      case a if a.contains(FromStorage) =>
        """
          |def table: _root_.java.lang.String = ???
          |def selectedFields: _root_.scala.List[_root_.java.lang.String] = ???
          |def rowRestriction: _root_.java.lang.String = ???
        """.stripMargin
      case _ => ""
    }
  }
}

final class BigQueryTypeInjector extends AnnotationTypeInjector {
  import AnnotationTypeInjector._
  import BigQueryTypeInjector._

  override def needsCompanionObject(source: ScTypeDefinition): Boolean = false

  override def injectFunctions(source: ScTypeDefinition): Seq[String] =
    source match {
      case c: ScClass if bqAnnotation(c).isDefined =>
        val parent = c.containingClass.getQualifiedName.init
        val caseClasses = generatedCaseClasses(parent, c).find(_.contains(CaseClassSuper))

        caseClasses
          .map(getApplyPropsSignature)
          .getOrElse(Seq.empty[String])
          .map(v => s"def $v = ???")
      case _ => Seq.empty
    }

  override def injectSupers(source: ScTypeDefinition): Seq[String] =
    source match {
      case c: ScClass if bqAnnotation(c).isDefined => Seq(CaseClassSuper)
      case _                                       => Seq.empty
    }

  /**
   * Main method of the plugin. Injects syntactic inner members like case classes and companion
   * objects, makes IntelliJ happy about BigQuery macros. Assumes macro is enclosed within
   * class/object.
   */
  override def injectInners(source: ScTypeDefinition): Seq[String] = {
    source.extendsBlock.members
      .collect {
        case c: ScClass if bqAnnotation(c).isDefined =>
          val (annotated, other) =
            generatedCaseClasses(source.getQualifiedName.init, c).partition(
              _.contains(CaseClassSuper)
            )
          (c, (annotated.headOption, other))
      }
      .collect { case (c, (Some(annotated), other)) =>
        val tupledMethod = getTupledMethod(c.getName, annotated)
        val applyPropsSignature =
          getApplyPropsSignature(annotated).mkString(",")
        val unapplyReturnTypes =
          getUnapplyReturnTypes(annotated).mkString(",")

        val extraCompanionMethod =
          fetchExtraBQTypeCompanionMethods(source, c)

        // TODO: missing extends and traits - are they needed?
        // $tn extends ${p(c, SType)}.HasSchema[$name] with ..$traits
        val companion =
          s"""|object ${c.getName} {
                |  def apply( $applyPropsSignature ): ${c.getName} = ???
                |  def unapply(x$$0: ${c.getName}): _root_.scala.Option[($unapplyReturnTypes)] = ???
                |  def fromTableRow: _root_.scala.Function1[_root_.com.google.api.services.bigquery.model.TableRow, ${c.getName} ] = ???
                |  def toTableRow: _root_.scala.Function1[ ${c.getName}, _root_.com.google.api.services.bigquery.model.TableRow] = ???
                |  def schema: _root_.com.google.api.services.bigquery.model.TableSchema = ???
                |  def toPrettyString(indent: Int = 0): String = ???
                |  $extraCompanionMethod
                |  $tupledMethod
                |}""".stripMargin

        // for some reason we need to remove supers if any
        companion +: other.map(s => s.substring(0, s.lastIndexOf(')') + 1))
      }
      .flatten
  }
}
