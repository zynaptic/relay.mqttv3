/*
 * Zynaptic Relay MQTTv3 Client - An asynchronous MQTT v3.1.1 client for Java.
 *
 * Copyright (c) 2019, Zynaptic Limited.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 *
 * Please visit www.zynaptic.com or contact reaction@zynaptic.com if you need
 * additional information or have any questions.
 */

package com.zynaptic.relay.mqttv3.core;

import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.util.logging.Level;

import com.zynaptic.reaction.Deferrable;
import com.zynaptic.reaction.Deferred;
import com.zynaptic.reaction.Logger;
import com.zynaptic.reaction.Reactor;
import com.zynaptic.reaction.sockets.SocketHandle;
import com.zynaptic.reaction.sockets.SocketService;

/**
 * Provides a received message queue implementation that polls the underlying
 * transport socket and then performs MQTT control packet parsing on the
 * received data.
 * 
 * @author Chris Holgate
 */
final class ReceiveQueue implements Deferrable<ByteBuffer, Void> {

  // Specify the default request size to be used when polling the transport
  // socket.
  private static final int DEFAULT_REQUEST_SIZE = 1024;

  private final Reactor reactor;
  private final Logger logger;
  private final SocketService socketService;
  private final SocketHandle socketHandle;
  private ByteBuffer receiveBuffer;
  private Deferred<ControlPacket> deferredReceive;

  /**
   * Provides protected constructor for instantiating receive queues.
   * 
   * @param reactor This is the reactor service which is to be used for
   *   asynchronous event management.
   * @param logger This is a logger component which may be used for logging
   *   receive queue activity.
   * @param socketService This is the socket service which is to be used for low
   *   level byte buffer management.
   * @param socketHandle This is the socket handle which is to be polled for
   *   received data.
   */
  ReceiveQueue(final Reactor reactor, final Logger logger, final SocketService socketService,
      final SocketHandle socketHandle) {
    this.reactor = reactor;
    this.logger = logger;
    this.socketService = socketService;
    this.socketHandle = socketHandle;
    receiveBuffer = null;
    deferredReceive = null;
  }

  /**
   * Initiate a packet receive request. Note that only a single packet receive
   * operation may be active at any given time.
   * 
   * @return Returns a deferred event object which will have its callbacks
   *   executed on completion. The callback parameter will be the next control
   *   packet to be received. If the socket has been closed an error callback will
   *   be made, passing an exception of type {@link SocketClosedException}.
   */
  synchronized Deferred<ControlPacket> receivePacket() {
    if (deferredReceive != null) {
      throw new IllegalStateException("Packet receive request already active");
    }

    // Create a deferred receive event before starting the packet parsing process.
    deferredReceive = reactor.newDeferred();
    final Deferred<ControlPacket> deferredReturn = deferredReceive.makeRestricted();
    parsePacket();
    return deferredReturn;
  }

  /*
   * Callback handler on completing a successful socket read request. Attempt to
   * parse the received data.
   */
  @Override
  public synchronized Void onCallback(final Deferred<ByteBuffer> deferred, final ByteBuffer readBuffer) {
    receiveBuffer = readBuffer;
    parsePacket();
    return null;
  }

  /*
   * Callback handler on completing a failed socket read request. It forwards all
   * exceptions directly to the original caller. This includes socket closed
   * exceptions which can initiate the connection retry cycle.
   */
  @Override
  public synchronized Void onErrback(final Deferred<ByteBuffer> deferred, final Exception error) {
    deferredReceive.errback(error);
    deferredReceive = null;
    return null;
  }

  /*
   * Parses the data in the receive buffer, returning the parsed MQTT packet as
   * the deferred receive callback parameter.
   */
  private void parsePacket() {

    // Request a new data buffer if no data is currently available.
    if (receiveBuffer == null) {
      socketHandle.read(DEFAULT_REQUEST_SIZE).addDeferrable(this, true);
      return;
    }

    // Attempt to parse the next packet. This can result in malformed message
    // exceptions which are passed back to the caller or buffer underflow exceptions
    // which result in a request for more data.
    final int startPosition = receiveBuffer.position();
    try {

      // Extract the main header byte and message length. If this fails the connection
      // is unrecoverable and must be closed.
      final int headerByte = 0xFF & receiveBuffer.get();
      final ControlPacketType controlPacketType = ControlPacketType.getControlPacketType(headerByte);
      if (controlPacketType == null) {
        throw new MalformedPacketException("Invalid control packet type");
      }
      final int messageLength = VarLenQuantity.create(receiveBuffer).getValue();

      // Determine whether there is sufficient data in the buffer to parse the full
      // message. If not, request further data in an expanded buffer.
      if (messageLength > receiveBuffer.remaining()) {
        receiveBuffer.position(startPosition);
        fillReceiveBuffer(5 + messageLength);
        return;
      }

      // Parse the packet body, based on the known control packet type.
      ControlPacket parsedPacket;
      switch (controlPacketType) {
      case CONNECT:
        parsedPacket = ConnectPacket.parsePacket(messageLength, receiveBuffer);
        break;
      case CONNACK:
        parsedPacket = ConnackPacket.parsePacket(messageLength, receiveBuffer);
        break;
      case PUBLISH:
        parsedPacket = PublishPacket.parsePacket(headerByte, messageLength, receiveBuffer);
        break;
      case SUBSCRIBE:
      case UNSUBSCRIBE:
        parsedPacket = SubscribePacket.parsePacket(controlPacketType, messageLength, receiveBuffer);
        break;
      case SUBACK:
        parsedPacket = SubackPacket.parsePacket(messageLength, receiveBuffer);
        break;
      default:
        parsedPacket = ControlPacket.parsePacket(controlPacketType, messageLength, receiveBuffer);
        break;
      }

      // Include packet tracing if required.
      if (logger.getLogLevel() == Level.FINER) {
        logger.log(Level.FINER, "RX " + parsedPacket.tracePacket(false, false));
      } else if (logger.getLogLevel() == Level.FINEST) {
        logger.log(Level.FINEST, "RX " + parsedPacket.tracePacket(false, false));
      }

      // Hand off the received packet.
      deferredReceive.callback(parsedPacket);
      deferredReceive = null;

      // After parsing the packet, release any fully consumed buffers.
      if (!receiveBuffer.hasRemaining()) {
        socketService.releaseByteBuffer(receiveBuffer);
        receiveBuffer = null;
      }
    }

    // Request more data on a buffer underflow. This doubles the size of the request
    // buffer and then attempts to fill it.
    catch (final BufferUnderflowException error) {
      receiveBuffer.position(startPosition);
      fillReceiveBuffer(2 * receiveBuffer.remaining());
    }

    // Handle fatal errors.
    catch (final Exception error) {
      deferredReceive.errback(error);
      deferredReceive = null;
    }
  }

  /*
   * Extends the receive buffer to the requested size and then issues a read to
   * the socket handle in order to fill it.
   */
  private void fillReceiveBuffer(final int requestedSize) {
    ByteBuffer readBuffer = receiveBuffer;
    if (requestedSize > receiveBuffer.capacity()) {
      readBuffer = socketService.getByteBuffer(requestedSize);
      readBuffer.put(receiveBuffer).flip();
      socketService.releaseByteBuffer(receiveBuffer);
    }
    receiveBuffer = null;
    socketHandle.read(readBuffer).addDeferrable(this, true);
  }
}
