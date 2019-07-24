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

package com.zynaptic.relay.mqttv3;

import java.util.EnumSet;
import java.util.Map;
import java.util.TreeMap;

/**
 * Defines the enumerated set of connection status values, as returned by the
 * MQTT server in response to a connection request.
 *
 * @author Chris Holgate
 */
public enum ConnectionStatus {

  /**
   * Connection accepted.
   */
  ACCEPTED(0),

  /**
   * Connection refused - unacceptable protocol version. The server does not
   * support the level of the MQTT protocol requested by the client.
   */
  UNSUPPORTED_PROTOCOL(1),

  /**
   * Connection refused - client identifier rejected. The client identifier is a
   * valid UTF-8 string, but is not allowed by the server.
   */
  IDENTIFIER_REJECTED(2),

  /**
   * Connection refused - server unavailable. The network connection has been
   * established but the MQTT service is currently unavailable.
   */
  SERVER_UNAVAILABLE(3),

  /**
   * Connection refused - bad user name or password. The data in the user name or
   * password does not conform to the MQTT specification.
   */
  INVALID_CREDENTIALS(4),

  /**
   * Connection refused - not authorised. The client is not authorised to connect
   * to the MQTT server.
   */
  NOT_AUTHORIZED(5);

  // Specify the mapping of integer encoding values to QoS policies.
  private static final Map<Integer, ConnectionStatus> encodingMap;

  // Specify the integer encoding for a given enumeration.
  private final int integerEncoding;

  /**
   * Determines the connection acknowledgement status given the integer encoding.
   *
   * @param integerEncoding This is the integer encoding of the connection
   *   acknowledgement status which is to be used for determining the connection
   *   status.
   * @return Returns the enumeration for the connection acknowledgement status
   *   that matches the integer encoding, or a null reference if no matching
   *   connection status is found.
   */
  public static ConnectionStatus getConnackStatus(final Integer integerEncoding) {
    if (integerEncoding == null) {
      return null;
    }
    return encodingMap.get(integerEncoding);
  }

  /**
   * Accesses the integer encoding value used to represent the connection
   * acknowledgement status.
   *
   * @return Returns the integer value which represents the connection
   *   acknowledgement status.
   */
  public int getIntegerEncoding() {
    return integerEncoding;
  }

  /*
   * Default constructor generates the enumeration parameters from the supplied
   * constants.
   */
  private ConnectionStatus(final int integerEncoding) {
    this.integerEncoding = integerEncoding;
  }

  /*
   * Static constructor builds the encoding map on startup.
   */
  static {
    encodingMap = new TreeMap<Integer, ConnectionStatus>();
    for (final ConnectionStatus connackStatus : EnumSet.allOf(ConnectionStatus.class)) {
      final Integer key = connackStatus.getIntegerEncoding();
      if (encodingMap.containsKey(key)) {
        throw new RuntimeException("Duplicate connection acknowledgement status encodings detected.");
      }
      encodingMap.put(key, connackStatus);
    }
  };
}
