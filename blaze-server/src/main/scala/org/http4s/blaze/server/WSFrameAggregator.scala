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

package org.http4s.blaze.server

import org.http4s.blaze.pipeline.MidStage
import org.http4s.blaze.server.WSFrameAggregator.Accumulator
import org.http4s.blaze.util.Execution._
import org.http4s.internal.bug
import org.http4s.websocket.WebSocketFrame
import org.http4s.websocket.WebSocketFrame._
import scodec.bits.ByteVector

import java.net.ProtocolException
import scala.annotation.tailrec
import scala.collection.mutable
import scala.concurrent.Future
import scala.concurrent.Promise
import scala.util.Failure
import scala.util.Success

private class WSFrameAggregator extends MidStage[WebSocketFrame, WebSocketFrame] {
  def name: String = "WebSocket Frame Aggregator"

  private[this] val accumulator = new Accumulator

  def readRequest(size: Int): Future[WebSocketFrame] = {
    val p = Promise[WebSocketFrame]()
    channelRead(size).onComplete {
      case Success(f) => readLoop(f, p)
      case Failure(t) => p.failure(t)
    }(directec)
    p.future
  }

  private def readLoop(frame: WebSocketFrame, p: Promise[WebSocketFrame]): Unit =
    frame match {
      case _: Text => handleHead(frame, p)
      case _: Binary => handleHead(frame, p)

      case c: Continuation =>
        if (accumulator.isEmpty) {
          val e = new ProtocolException(
            "Invalid state: Received a Continuation frame without accumulated state.")
          logger.error(e)("Invalid state")
          p.failure(e)
          ()
        } else {
          accumulator.append(frame)
          if (c.last) {
            // We are finished with the segment, accumulate
            p.success(accumulator.take())
            ()
          } else
            channelRead().onComplete {
              case Success(f) =>
                readLoop(f, p)
              case Failure(t) =>
                p.failure(t)
                ()
            }(trampoline)
        }

      case f =>
        // Must be a control frame, send it out
        p.success(f)
        ()
    }

  private def handleHead(frame: WebSocketFrame, p: Promise[WebSocketFrame]): Unit =
    if (!accumulator.isEmpty) {
      val e = new ProtocolException(s"Invalid state: Received a head frame with accumulated state")
      accumulator.clear()
      p.failure(e)
      ()
    } else if (frame.last) {
      // Head frame that is complete
      p.success(frame)
      ()
    } else {
      // Need to start aggregating
      accumulator.append(frame)
      channelRead().onComplete {
        case Success(f) =>
          readLoop(f, p)
        case Failure(t) =>
          p.failure(t)
          ()
      }(directec)
    }

  // Just forward write requests
  def writeRequest(data: WebSocketFrame): Future[Unit] = channelWrite(data)
  override def writeRequest(data: collection.Seq[WebSocketFrame]): Future[Unit] = channelWrite(data)
}

private object WSFrameAggregator {
  private final class Accumulator {
    private[this] val queue = new mutable.Queue[WebSocketFrame]
    private[this] var size = 0

    def isEmpty: Boolean = queue.isEmpty

    def append(frame: WebSocketFrame): Unit = {
      // The first frame needs to not be a continuation
      if (queue.isEmpty) frame match {
        case _: Text | _: Binary => // nop
        case f =>
          throw bug(s"Shouldn't get here. Wrong type: ${f.getClass.getName}")
      }
      size += frame.length
      queue += frame
      ()
    }

    def take(): WebSocketFrame = {
      val isText = queue.head match {
        case _: Text => true
        case _: Binary => false
        case f =>
          // shouldn't happen as it's guarded for in `append`
          val e = bug(s"Shouldn't get here. Wrong type: ${f.getClass.getName}")
          throw e
      }

      var out = ByteVector.empty
      @tailrec
      def go(): Unit =
        if (!queue.isEmpty) {
          val frame = queue.dequeue().data
          out ++= frame
          go()
        }
      go()

      size = 0
      if (isText) Text(out) else Binary(out)
    }

    def clear(): Unit = {
      size = 0
      queue.clear()
    }
  }
}
