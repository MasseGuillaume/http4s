/*
 * Copyright 2013 http4s.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.http4s.blaze.demo.server.endpoints

import cats.effect.Async
import cats.syntax.flatMap._
import io.circe.generic.auto._
import org.http4s.circe._
import org.http4s.dsl.Http4sDsl
import org.http4s.{ApiVersion => _, _}

import scala.xml._

// Docs: http://http4s.org/latest/entity/
class JsonXmlHttpEndpoint[F[_]](implicit F: Async[F]) extends Http4sDsl[F] {
  private case class Person(name: String, age: Int)

  /** XML Example for Person:
    *
    * <person>
    *   <name>gvolpe</name>
    *   <age>30</age>
    * </person>
    */
  private object Person {
    def fromXml(elem: Elem): Person = {
      val name = (elem \\ "name").text
      val age = (elem \\ "age").text
      Person(name, age.toInt)
    }
  }

  private def personXmlDecoder: EntityDecoder[F, Person] =
    org.http4s.scalaxml.xml[F].map(Person.fromXml)

  private implicit def jsonXmlDecoder: EntityDecoder[F, Person] =
    jsonOf[F, Person].orElse(personXmlDecoder)

  val service: HttpRoutes[F] = HttpRoutes.of {
    case GET -> Root / ApiVersion / "media" =>
      Ok(
        "Send either json or xml via POST method. Eg: \n{\n  \"name\": \"gvolpe\",\n  \"age\": 30\n}\n or \n <person>\n  <name>gvolpe</name>\n  <age>30</age>\n</person>")

    case req @ POST -> Root / ApiVersion / "media" =>
      req.as[Person].flatMap { person =>
        Ok(s"Successfully decoded person: ${person.name}")
      }
  }
}
