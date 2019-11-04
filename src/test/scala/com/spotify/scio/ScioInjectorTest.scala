/*
 * Copyright 2017 Spotify AB.
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

import org.scalatest.{FlatSpec, Matchers}

class ScioInjectorTest extends FlatSpec with Matchers {
  private val si = new ScioInjector()
  private val className = "Foobar"

  "Tupled method" should "be empty on case class with 1 parameter" in {
    val input = Seq(
      s"""|case class $className(f1 : _root_.scala.Option[_root_.java.lang.String])
                        |extends _root_.com.spotify.scio.bigquery.types.BigQueryType.HasAnnotation
                        |""".stripMargin.replace("\n", " ")
    )
    ScioInjector.getTupledMethod(className, input) shouldBe empty
  }

  it should "work on case class with 2 parameters" in {
    val input = Seq(
      s"""|case class $className(f1 : _root_.scala.Option[_root_.java.lang.String],
                        |f2 : _root_.scala.Option[_root_.java.lang.Long])
                         |extends _root_.com.spotify.scio.bigquery.types.BigQueryType.HasAnnotation
                        |""".stripMargin.replace("\n", " ")
    )
    val expected =
      s"def tupled: _root_.scala.Function1[( _root_.scala.Option[_root_.java.lang.String] , _root_.scala.Option[_root_.java.lang.Long] ), $className ] = ???"
    ScioInjector.getTupledMethod(className, input) shouldBe expected
  }

  it should "be empty on case class more than 22 parameters" in {
    val input = Seq(
      s"""|case class $className(f1 : _root_.scala.Option[_root_.java.lang.String],
                        |f2 : _root_.scala.Option[_root_.java.lang.Long],
                        |f3 : _root_.scala.Option[_root_.java.lang.Long],
                        |f4 : _root_.scala.Option[_root_.java.lang.Long],
                        |f5 : _root_.scala.Option[_root_.java.lang.Long],
                        |f6 : _root_.scala.Option[_root_.java.lang.Long],
                        |f7 : _root_.scala.Option[_root_.java.lang.Long],
                        |f8 : _root_.scala.Option[_root_.java.lang.Long],
                        |f9 : _root_.scala.Option[_root_.java.lang.Long],
                        |f10 : _root_.scala.Option[_root_.java.lang.Long],
                        |f11 : _root_.scala.Option[_root_.java.lang.Long],
                        |f12 : _root_.scala.Option[_root_.java.lang.Long],
                        |f13 : _root_.scala.Option[_root_.java.lang.Long],
                        |f14 : _root_.scala.Option[_root_.java.lang.Long],
                        |f15 : _root_.scala.Option[_root_.java.lang.Long],
                        |f16 : _root_.scala.Option[_root_.java.lang.Long],
                        |f17 : _root_.scala.Option[_root_.java.lang.Long],
                        |f18 : _root_.scala.Option[_root_.java.lang.Long],
                        |f19 : _root_.scala.Option[_root_.java.lang.Long],
                        |f20 : _root_.scala.Option[_root_.java.lang.Long],
                        |f21 : _root_.scala.Option[_root_.java.lang.Long],
                        |f22 : _root_.scala.Option[_root_.java.lang.Long],
                        |f23 : _root_.scala.Option[_root_.java.lang.Long],
                        |extends _root_.com.spotify.scio.bigquery.types.BigQueryType.HasAnnotation
                        |""".stripMargin.replace("\n", " ")
    )
    ScioInjector.getTupledMethod(className, input) shouldBe empty
  }

  it should "work on case class with Map field" in {
    val input = Seq(
      s"""|case class $className(f1 : _root_.scala.Option[_root_.java.lang.String],
                        |f2 : _root_.scala.Option[_root_.scala.collection.Map[_root_.java.lang.String, _root_.java.lang.String]])
                        |extends _root_.com.spotify.scio.bigquery.types.BigQueryType.HasAnnotation
                        |""".stripMargin.replace("\n", " ")
    )
    val expected =
      s"def tupled: _root_.scala.Function1[( _root_.scala.Option[_root_.java.lang.String] , _root_.scala.Option[_root_.scala.collection.Map[_root_.java.lang.String, _root_.java.lang.String]] ), $className ] = ???"
    ScioInjector.getTupledMethod(className, input) shouldBe expected
  }

  it should "return the unapply return types on case class with 3 parameters" in {
    val input = Seq(s"""|case class $className(
                        |f1 : _root_.scala.Option[_root_.java.lang.String],
                        |f2 : _root_.scala.Option[_root_.java.lang.Long],
                        |f2 : _root_.scala.Option[_root_.java.lang.Int])
                        |extends _root_.com.spotify.scio.bigquery.types.BigQueryType.HasAnnotation
                        |""".stripMargin.replace("\n", " "))
    val expected = Seq(
      "_root_.scala.Option[_root_.java.lang.String]",
      "_root_.scala.Option[_root_.java.lang.Long]",
      "_root_.scala.Option[_root_.java.lang.Int]"
    )
    ScioInjector.getUnapplyReturnTypes(input) shouldBe expected
  }
}
