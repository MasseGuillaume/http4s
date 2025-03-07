/*
 * Copyright 2014 http4s.org
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

package org.http4s
package blaze
package client

import cats.effect._
import cats.effect.std.Semaphore
import cats.syntax.all._
import org.http4s.client.Connection
import org.http4s.client.ConnectionBuilder
import org.http4s.client.RequestKey

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.Duration

/** Type that is responsible for the client lifecycle
  *
  * The [[ConnectionManager]] is a general wrapper around a [[ConnectionBuilder]]
  * that can pool resources in order to conserve resources such as socket connections,
  * CPU time, SSL handshakes, etc. Because it can contain significant resources it
  * must have a mechanism to free resources associated with it.
  */
private trait ConnectionManager[F[_], A <: Connection[F]] {

  /** Bundle of the connection and whether its new or not */
  // Sealed, rather than final, because SI-4440.
  sealed case class NextConnection(connection: A, fresh: Boolean)

  /** Shutdown this client, closing any open connections and freeing resources */
  def shutdown: F[Unit]

  /** Get a connection for the provided request key. */
  def borrow(requestKey: RequestKey): F[NextConnection]

  /** Release a connection.  The connection manager may choose to keep the connection for
    * subsequent calls to [[borrow]], or dispose of the connection.
    */
  def release(connection: A): F[Unit]

  /** Invalidate a connection, ensuring that its resources are freed.  The connection
    * manager may not return this connection on another borrow.
    */
  def invalidate(connection: A): F[Unit]
}

private object ConnectionManager {
  trait Stateful[F[_], A <: Connection[F]] extends ConnectionManager[F, A] {
    def state: BlazeClientState[F]
  }

  /** Create a [[ConnectionManager]] that creates new connections on each request
    *
    * @param builder generator of new connections
    */
  def basic[F[_]: Sync, A <: Connection[F]](
      builder: ConnectionBuilder[F, A]): ConnectionManager[F, A] =
    new BasicManager[F, A](builder)

  /** Create a [[ConnectionManager]] that will attempt to recycle connections
    *
    * @param builder generator of new connections
    * @param maxTotal max total connections
    * @param maxWaitQueueLimit maximum number requests waiting for a connection at any specific time
    * @param maxConnectionsPerRequestKey Map of RequestKey to number of max connections
    * @param executionContext `ExecutionContext` where async operations will execute
    */
  def pool[F[_]: Async, A <: Connection[F]](
      builder: ConnectionBuilder[F, A],
      maxTotal: Int,
      maxWaitQueueLimit: Int,
      maxConnectionsPerRequestKey: RequestKey => Int,
      responseHeaderTimeout: Duration,
      requestTimeout: Duration,
      executionContext: ExecutionContext): F[ConnectionManager.Stateful[F, A]] =
    Semaphore(1).map { semaphore =>
      new PoolManager[F, A](
        builder,
        maxTotal,
        maxWaitQueueLimit,
        maxConnectionsPerRequestKey,
        responseHeaderTimeout,
        requestTimeout,
        semaphore,
        executionContext)
    }
}
