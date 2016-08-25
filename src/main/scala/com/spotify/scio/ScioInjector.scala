package com.spotify.scio

import java.nio.charset.Charset

import com.google.common.base.Charsets
import com.google.common.io.Files
import com.intellij.openapi.diagnostic.Logger
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef.SyntheticMembersInjector

class ScioInjector extends SyntheticMembersInjector {
  val logger = Logger.getInstance(classOf[ScioInjector])

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
      case c: ScClass if c.annotationNames.contains("BigQueryType.fromQuery") =>
        log(s"Found BQ.fromQuery in ${source.getName}")

        val caseClasses = findClassFile(c.getName).map(f => {
          import collection.JavaConverters._
          Files.readLines(f, Charset.defaultCharset()).asScala.filter(_.contains("case class"))
        }).getOrElse(Seq.empty)

        val companion = s"""|object ${c.getName} {
                            |  def fromTableRow: _root_.scala.Function1[_root_.com.google.api.services.bigquery.model.TableRow, ${c.getName}] = ???
                            |  def toTableRow: _root_.scala.Function1[${c.getName}, _root_.com.google.api.services.bigquery.model.TableRow] = ???
                            |  def schema: _root_.com.google.api.services.bigquery.model.TableSchema = ???
                            |  def toPrettyString(indent: Int = 0): String = ???
                            |  def query: _root_.java.lang.String = ???
                            |}""".stripMargin

        caseClasses ++ Seq(companion)
      case _ => Seq.empty
    }
  }
}