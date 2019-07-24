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

import java.util.EnumSet;
import java.util.Map;
import java.util.TreeMap;

/**
 * Defines the enumerated set of persisted handshake states.
 *
 * @author Chris Holgate
 */
enum HandshakeState {

  /**
   * This specifies that a QoS 1 (at least once) publish transaction has been
   * initiated, but that a matching acknowledgement packet (PUBACK) has not yet
   * been received.
   */
  QOS1_TX_PUBLISH((short) 0x6CD8),

  /**
   * This specifies that a QoS 2 (exactly once) publish transaction has been
   * initiated, but that a matching publish received packet (PUBREC) has not yet
   * been received.
   */
  QOS2_TX_PUBLISH((short) 0x595F),

  /**
   * This specifies that a QoS 2 (exactly once) publish transaction is in
   * progress, with a publish release packet (PUBREL) having been sent, but where
   * a matching publish complete packet (PUBCOMP) has not yet been received.
   */
  QOS2_TX_RELEASE((short) 0xCDAE),

  /**
   * This specifies that a QoS 2 (exactly once) receive transaction is in
   * progress, with a publish received packet (PUBREC) having been sent, but where
   * a matching publish release packet (PUBREL) has not yet been received.
   */
  QOS2_RX_PROCESS((short) 0x41B4),

  /**
   * This specifies that a subscribe request is in progress, with a subscribe
   * request packet (SUBSCRIBE) having been sent, but where a matching subscribe
   * acknowledgement packet (SUBACK) has not yet been received.
   */
  SUBSCRIBE_REQUEST((short) 0x628C),

  /**
   * This specifies that an unsubscribe request is in progress, with an
   * unsubscribe request packet (UNSUBSCRIBE) having been sent, but where a
   * matching unsubscribe acknowledgement packet (UNSUBACK) has not yet been
   * received.
   */
  UNSUBSCRIBE_REQUEST((short) 0x83AF);

  // Specify the mapping of integer encoding values to handshake states.
  private static Map<Short, HandshakeState> encodingMap;

  // Specify the integer encoding for a given enumeration.
  private final short integerEncoding;

  /**
   * Determines the handshake state, given the integer encoding used to represent
   * it.
   *
   * @param integerEncoding This is the integer encoding of the handshake state
   *   which is to be accessed.
   * @return Returns the handshake state associated with the given integer
   *   encoding, or a null reference if no such state exists.
   */
  static HandshakeState getHandshakeState(final Short integerEncoding) {
    return (integerEncoding == null) ? null : encodingMap.get(integerEncoding);
  }

  /**
   * Accesses the integer encoding used to identify the handshake state.
   *
   * @return Returns the integer value that represents the handshake state.
   */
  short getIntegerEncoding() {
    return integerEncoding;
  }

  /*
   * Default constructor generates the enumeration parameters from the supplied
   * constants.
   */
  private HandshakeState(final short integerEncoding) {
    this.integerEncoding = integerEncoding;
  }

  /*
   * Static constructor builds the encoding map on startup.
   */
  static {
    encodingMap = new TreeMap<Short, HandshakeState>();
    for (final HandshakeState handshakeState : EnumSet.allOf(HandshakeState.class)) {
      final Short key = handshakeState.getIntegerEncoding();
      if (encodingMap.containsKey(key)) {
        throw new RuntimeException("Duplicate handshake state encodings detected.");
      }
      encodingMap.put(key, handshakeState);
    }
  };
}
