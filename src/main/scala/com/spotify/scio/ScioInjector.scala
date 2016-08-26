package com.spotify.scio

import java.nio.charset.Charset

import com.google.common.base.Charsets
import com.google.common.io.Files
import com.intellij.openapi.diagnostic.Logger
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef.SyntheticMembersInjector

class ScioInjector extends SyntheticMembersInjector {
  private val logger = Logger.getInstance(classOf[ScioInjector])

  // Could not find a way to get fully qualified annotation names
  // even tho there is API, it does not return the annotations.
  // For now stick with relative annotation name.
  private val BQTNamespace = "BigQueryType"
  private val fromQuery = s"$BQTNamespace.fromQuery"
  private val fromTable = s"$BQTNamespace.fromTable"
  private val annotations = Seq(fromQuery,
                                fromTable,
                                s"$BQTNamespace.fromSchema",
                                s"$BQTNamespace.toTable")


  // This has to stay in sync with the Scio implementation
  private def getBQClassCacheDir = {
    //TODO: add this as key/value settings with default etc
    if (sys.props("bigquery.class.cache.directory") != null) {
      sys.props("bigquery.class.cache.directory")
    } else {
      sys.props("java.io.tmpdir") + "bigquery-classes"
    }
  }

  def findClassFile(clazz: String): Option[java.io.File] = {
    val classFielePath = getBQClassCacheDir + s"/$clazz.scala"
    val classFile = new java.io.File(classFielePath)
    log(s"Looking for $classFielePath" )
    if (classFile.exists()) Some(classFile) else None
  }

  def log(s: String): Unit = {
    logger.debug(s)
  }

  override def injectInners(source: ScTypeDefinition): Seq[String] = {
    source.members.flatMap {
      case c: ScClass if c.annotationNames.exists(annotations.contains) =>
        val annotation = c.annotationNames.find(annotations.contains).get
        log(s"Found $annotation in ${source.getName}")

        val caseClasses = findClassFile(c.getName).map(f => {
          import collection.JavaConverters._
          Files.readLines(f, Charset.defaultCharset()).asScala.filter(_.contains("case class"))
        }).getOrElse(Seq.empty)

        val extraCompanionMethod = annotation match {
          case a if a.equals(fromQuery) => "def query: _root_.java.lang.String = ???"
          case a if a.equals(fromTable) => "def table: _root_.com.google.api.services.bigquery.model.TableReference = ???"
          case _ => ""
        }

        //TODO: what about tupled?
        val companion = s"""|object ${c.getName} {
                            |  def fromTableRow: _root_.scala.Function1[_root_.com.google.api.services.bigquery.model.TableRow, ${c.getName}] = ???
                            |  def toTableRow: _root_.scala.Function1[${c.getName}, _root_.com.google.api.services.bigquery.model.TableRow] = ???
                            |  def schema: _root_.com.google.api.services.bigquery.model.TableSchema = ???
                            |  def toPrettyString(indent: Int = 0): String = ???
                            |  $extraCompanionMethod
                            |}""".stripMargin

        caseClasses ++ Seq(companion)
      case _ => Seq.empty
    }
  }
}