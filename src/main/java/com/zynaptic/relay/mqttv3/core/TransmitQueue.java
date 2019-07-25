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

import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.logging.Level;

import com.zynaptic.reaction.Deferrable;
import com.zynaptic.reaction.Deferred;
import com.zynaptic.reaction.Logger;
import com.zynaptic.reaction.Reactor;
import com.zynaptic.reaction.Timeable;
import com.zynaptic.reaction.sockets.SocketClosedException;
import com.zynaptic.reaction.sockets.SocketHandle;
import com.zynaptic.reaction.sockets.SocketService;

/**
 * Provides a transmit message queue implementation that combines MQTT packets
 * generated over a single transmit period and then forwards them as a single
 * transport layer transaction. This should reduce the number of small, high
 * overhead transport layer packets being transferred.
 *
 * @author Chris Holgate
 */
final class TransmitQueue implements Timeable<Void>, Deferrable<ByteBuffer, Void> {

  // Specify the internal state space for the transmit queue.
  private enum QueueState {
    IDLE, WAITING, SENDING, DISCARDING
  }

  private final Reactor reactor;
  private final Logger logger;
  private final SocketService socketService;
  private final SocketHandle socketHandle;
  private final int transmitInterval;
  private final LinkedList<ByteBuffer> packetQueue;
  private QueueState queueState;

  /**
   * Provides protected constructor for instantiating transmit queues.
   *
   * @param reactor This is the reactor service which is to be used for
   *   asynchronous event management.
   * @param logger This is a logger component which may be used for logging
   *   transmit queue activity.
   * @param socketService This is the socket service which is to be used for low
   *   level byte buffer management.
   * @param socketHandle This is the socket handle which is to be used for
   *   transmitting data.
   * @param transmitInterval This is the minimum interval between transport later
   *   transmissions, expressed as an integer number of milliseconds.
   */
  TransmitQueue(final Reactor reactor, final Logger logger, final SocketService socketService,
      final SocketHandle socketHandle, final int transmitInterval) {
    this.reactor = reactor;
    this.logger = logger;
    this.socketService = socketService;
    this.socketHandle = socketHandle;
    this.transmitInterval = transmitInterval;
    packetQueue = new LinkedList<ByteBuffer>();
    queueState = QueueState.IDLE;
  }

  /**
   * Initiate a packet transmit request. This formats the packet for transmission
   * and appends it to the transmit queue.
   *
   * @param controlPacket This is the control packet that is being queued for
   *   transmission.
   */
  void transmitPacket(final ControlPacket controlPacket) {
    if (logger.getLogLevel() == Level.FINER) {
      logger.log(Level.FINER, "TX " + controlPacket.tracePacket(false, false));
    } else if (logger.getLogLevel() == Level.FINEST) {
      logger.log(Level.FINEST, "TX " + controlPacket.tracePacket(false, true));
    }
    transmitRawPacket(controlPacket.formatPacket());
  }

  /**
   * Initiate a raw packet transmit request. This takes a pre-formatted control
   * packet and appends it to the transmit queue.
   *
   * @param rawPacket This is a byte buffer that contains the formatted control
   *   packet.
   */
  synchronized void transmitRawPacket(final ByteBuffer rawPacket) {
    if (queueState != QueueState.DISCARDING) {
      packetQueue.add(rawPacket);
      if (queueState == QueueState.IDLE) {
        queueState = QueueState.WAITING;
        reactor.runTimerOneShot(this, transmitInterval, null);
      }
    }
  }

  /*
   * Callback after the transmit interval timer has expired. Prepare the queued
   * control packets for transmission by aggregating them into a single transmit
   * buffer.
   */
  @Override
  public synchronized void onTick(final Void data) {
    if (queueState == QueueState.WAITING) {
      int outputBufferSize = 0;
      final ByteBuffer[] packetBuffers = new ByteBuffer[packetQueue.size()];
      for (int i = 0; i < packetBuffers.length; i++) {
        final ByteBuffer nextPacketBuffer = packetQueue.remove();
        packetBuffers[i] = nextPacketBuffer;
        outputBufferSize += nextPacketBuffer.remaining();
      }
      final ByteBuffer outputBuffer = socketService.getByteBuffer(outputBufferSize);
      for (int i = 0; i < packetBuffers.length; i++) {
        outputBuffer.put(packetBuffers[i]);
      }
      queueState = QueueState.SENDING;
      socketHandle.write(outputBuffer.flip()).addDeferrable(this, true);
    }
  }

  /*
   * Callback on completion of successful data transmission.
   */
  @Override
  public synchronized Void onCallback(final Deferred<ByteBuffer> deferred, final ByteBuffer residualBuffer) {

    // Continue transmitting residual data if required.
    if (residualBuffer != null) {
      socketHandle.write(residualBuffer).addDeferrable(this, true);
    }

    // Enter the idle state if all outstanding data has been transmitted.
    else if (packetQueue.isEmpty()) {
      queueState = QueueState.IDLE;
    }

    // Schedule timed callback if there are queued packets waiting.
    else {
      queueState = QueueState.WAITING;
      reactor.runTimerOneShot(this, transmitInterval, null);
    }
    return null;
  }

  /*
   * Callback on completion of failed data transmission. This silently discards
   * data and relies on the receive queue processing to identify socket closed
   * conditions.
   */
  @Override
  public synchronized Void onErrback(final Deferred<ByteBuffer> deferred, final Exception error) {
    if (error instanceof SocketClosedException) {
      logger.log(Level.FINE, "Transmit queue socket closed - discarding subsequent packets");
    } else {
      logger.log(Level.WARNING, "Unexpected transmit queue socket error - discarding subsequent packets", error);
    }
    packetQueue.clear();
    queueState = QueueState.DISCARDING;
    return null;
  }
}
