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

import com.zynaptic.reaction.Deferred;

/**
 * Implements a handshake table entry that encapsulates all the information
 * required to manage a single handshake transaction.
 *
 * @author Chris Holgate
 */
final class HandshakeInfo {

  private HandshakeState handshakeState;
  private final short packetId;
  private short sequenceId;
  private Deferred<ControlPacket> deferredResponse;

  /**
   * Provides a protected constructor for initialising a handshake table entry.
   *
   * @param handshakeState This is the handshake state which is to be stored for
   *   the handshake table entry.
   * @param packetId This is the packet identifier which is to be used for
   *   uniquely identifying the handshake.
   * @param sequenceId This is a sequence identifier which is used to enforce
   *   correct retransmission order.
   */
  HandshakeInfo(final HandshakeState handshakeState, final short packetId, final short sequenceId) {
    this.handshakeState = handshakeState;
    this.packetId = packetId;
    this.sequenceId = sequenceId;
    deferredResponse = null;
  }

  /**
   * Updates the current handshake state and associated sequence identifier.
   *
   * @param handshakeState This is the new handshake state which is to be stored
   *   for the handshake table entry.
   * @param sequenceId This is the new sequence identifier which is used to
   *   enforce correct retransmission order.
   */
  synchronized void setHandshakeState(final HandshakeState handshakeState, final short sequenceId) {
    this.handshakeState = handshakeState;
    this.sequenceId = sequenceId;
  }

  /**
   * Accesses the current handshake state.
   *
   * @return Returns the current state for the handshake operation.
   */
  synchronized HandshakeState getHandshakeState() {
    return handshakeState;
  }

  /**
   * Associates a new deferred event object with the handshake operation. This
   * allows the correct initiator to be notified of handshake completion.
   *
   * @param deferredResponse This is a deferred event object which may be used to
   *   notify handshake completion.
   */
  synchronized void setDeferredResponse(final Deferred<ControlPacket> deferredResponse) {
    this.deferredResponse = deferredResponse;
  }

  /**
   * Accesses the deferred event object that is associated with the handshake
   * operation. This allows the correct initiator to be notified of handshake
   * completion.
   *
   * @return Returns a deferred event object which may be used to notify handshake
   *   completion.
   */
  synchronized Deferred<ControlPacket> getDeferredResponse() {
    return deferredResponse;
  }

  /**
   * Accesses the packet identifier that is associated with the handshake
   * operation.
   *
   * @return Returns the packet identifier for the handshake operation.
   */
  short getPacketId() {
    return packetId;
  }

  /**
   * Accesses the current sequence identifier that is associated with the
   * handshake operation.
   *
   * @return Returns the sequence identifier for the handshake operation.
   */
  synchronized short getSequenceId() {
    return sequenceId;
  }
}
