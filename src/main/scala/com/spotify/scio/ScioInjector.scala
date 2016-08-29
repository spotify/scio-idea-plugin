package com.spotify.scio

import java.nio.charset.Charset

import com.google.common.base.Charsets
import com.google.common.hash.Hashing
import com.google.common.io.Files
import com.intellij.openapi.diagnostic.Logger
import com.intellij.psi.PsiElement
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
  // otherwise we would have to pull in Scio deps
  private def getBQClassCacheDir = {
    //TODO: add this as key/value settings with default etc
    if (sys.props("bigquery.class.cache.directory") != null) {
      sys.props("bigquery.class.cache.directory")
    } else {
      sys.props("java.io.tmpdir") + "bigquery-classes"
    }
  }

  def findClassFile(fileName: String): Option[java.io.File] = {
    val classFilePath = getBQClassCacheDir + s"/$fileName"
    val classFile = new java.io.File(classFilePath)
    if (classFile.exists()) {
      logger.debug(s"Found $classFilePath")
      Some(classFile)
    } else {
      logger.error(s"""|Scio plugin could not find scala files for code completion.
                       |Please compile the project. Was looking for: $classFilePath""".stripMargin)
      None
    }
  }

  private def genHashForMacro(owner: String, srcFile: String): String = {
    Hashing.murmur3_32().newHasher()
      .putString(owner, Charsets.UTF_8)
      .putString(srcFile, Charsets.UTF_8)
      .hash().toString
  }

  override def injectInners(source: ScTypeDefinition): Seq[String] = {
    //TODO: what if the annotation is outside the object/class?
    source.members.flatMap {
      case c: ScClass if c.annotationNames.exists(annotations.contains) =>

        // For some reason sometimes [[getVirtualFile]] returns null. I don't know why.
        val fileName = Option(c.asInstanceOf[PsiElement].getContainingFile.getVirtualFile)
          .map(_.getCanonicalPath)

        val annotation = c.annotationNames.find(annotations.contains).get
        logger.debug(s"Found $annotation in ${source.getTruncedQualifiedName}")

        val hash = fileName.map(genHashForMacro(source.getTruncedQualifiedName, _))

        val caseClasses = hash.flatMap(h => findClassFile(s"${c.getName}-$h.scala")).map(f => {
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