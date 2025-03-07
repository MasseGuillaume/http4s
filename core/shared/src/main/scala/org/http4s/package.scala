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

package org

import cats.data._
import com.comcast.ip4s
import fs2.Stream
import org.typelevel.ci.CIString

import scala.annotation.nowarn

package object http4s {

  type AuthScheme = CIString

  type EntityBody[+F[_]] = Stream[F, Byte]

  val EmptyBody: EntityBody[Nothing] = Stream.empty[Nothing]

  val ApiVersion: Http4sVersion = Http4sVersion(BuildInfo.apiVersion._1, BuildInfo.apiVersion._2)

  type DecodeResult[F[_], A] = EitherT[F, DecodeFailure, A]

  type ParseResult[+A] = Either[ParseFailure, A]

  @deprecated("Use Charset.`UTF-8` directly", "0.22.8")
  val DefaultCharset = Charset.`UTF-8`

  /** A kleisli with a [[Request]] input and a [[Response]] output.  This type
    * is useful for writing middleware that are polymorphic over the return
    * type F.
    *
    * @tparam F the effect type in which the [[Response]] is returned
    * @tparam G the effect type of the [[Request]] and [[Response]] bodies
    */
  type Http[F[_], G[_]] = Kleisli[F, Request[G], Response[G]]

  /** A kleisli with a [[Request]] input and a [[Response]] output, such
    * that the response effect is the same as the request and response bodies'.
    * An HTTP app is total on its inputs.  An HTTP app may be run by a server,
    * and a client can be converted to or from an HTTP app.
    *
    * @tparam F the effect type in which the [[Response]] is returned, and also
    * of the [[Request]] and [[Response]] bodies.
    */
  type HttpApp[F[_]] = Http[F, F]

  /** A kleisli with a [[Request]] input and a [[Response]] output, such
    * that the response effect is an optional inside the effect of the
    * request and response bodies.  HTTP routes can conveniently be
    * constructed from a partial function and combined as a
    * `SemigroupK`.
    *
    * @tparam F the effect type of the [[Request]] and [[Response]] bodies,
    * and the base monad of the `OptionT` in which the response is returned.
    */
  type HttpRoutes[F[_]] = Http[OptionT[F, *], F]

  type AuthedRequest[F[_], T] = ContextRequest[F, T]

  /** The type parameters need to be in this order to make partial unification
    * trigger. See https://github.com/http4s/http4s/issues/1506
    */
  type AuthedRoutes[T, F[_]] = Kleisli[OptionT[F, *], AuthedRequest[F, T], Response[F]]

  type ContextRoutes[T, F[_]] = Kleisli[OptionT[F, *], ContextRequest[F, T], Response[F]]

  type Callback[A] = Either[Throwable, A] => Unit

  /** A stream of server-sent events */
  type EventStream[F[_]] = Stream[F, ServerSentEvent]

  // Syntax to enable JS-cross compilation without splitting files
  // Nobody will actually be able to run these methods b/c they will fail to link

  @nowarn
  private[http4s] implicit class Fs2IoPathCompanionOps(p: fs2.io.file.Path.type) {
    def fromNioPath(path: java.nio.file.Path): fs2.io.file.Path =
      throw new UnsupportedOperationException
  }

  private[http4s] implicit class Fs2IoPathOps(p: fs2.io.file.Path) {
    def toNioPath: java.nio.file.Path = throw new UnsupportedOperationException
  }

  @nowarn
  private[http4s] implicit class Ip4sIpv4AddressCompanionOps(ip: ip4s.Ipv4Address.type) {
    def fromInet4Address(a: java.net.Inet4Address): ip4s.Ipv4Address =
      throw new UnsupportedOperationException
  }

  private[http4s] implicit class Ip4sIpv4AddressOps(ip: ip4s.Ipv4Address) {
    def toInetAddress: java.net.Inet4Address = throw new UnsupportedOperationException
  }

  @nowarn
  private[http4s] implicit class Ip4sIpv6AddressCompanionOps(ip: ip4s.Ipv6Address.type) {
    def fromInet6Address(a: java.net.Inet6Address): ip4s.Ipv6Address =
      throw new UnsupportedOperationException
  }

  private[http4s] implicit class Ip4sIpv6AddressOps(ip: ip4s.Ipv6Address) {
    def toInetAddress: java.net.Inet6Address = throw new UnsupportedOperationException
  }

  private[http4s] implicit class Ip4sIpAddressOps(ip: ip4s.IpAddress) {
    def toInetAddress: java.net.InetAddress = throw new UnsupportedOperationException
  }
}
