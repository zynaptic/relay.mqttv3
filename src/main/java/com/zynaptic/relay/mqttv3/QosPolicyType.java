/*
 * Zynaptic Relay MQTTv3 - An MQTT version 3.1.1 implementation for Java.
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
 * Defines the enumerated set of Quality of Service (QoS) policies, as used to
 * select the publish handshake mechanism for a given publish message transfer.
 *
 * @author Chris Holgate
 */
public enum QosPolicyType {

  /**
   * This specifies QoS level 0, which is the minimum effort delivery mechanism.
   */
  AT_MOST_ONCE(0x00),

  /**
   * This specifies QoS level 1, which uses a simple acknowledgement handshake
   * mechanism.
   */
  AT_LEAST_ONCE(0x01),

  /**
   * This specifies QoS level 2, which uses a confirmed delivery handshake
   * mechanism.
   */
  EXACTLY_ONCE(0x02),

  /**
   * This specifies an invalid QoS setting and is used in subscribe response
   * messages to indicate a failure to add a subscriber with a given topic filter.
   */
  INVALID(0x80);

  // Specify the mapping of integer encoding values to QoS policies.
  private static final Map<Integer, QosPolicyType> encodingMap;

  // Specify the integer encoding for a given enumeration.
  private final int integerEncoding;

  /**
   * Determines the QoS policy type given the integer encoding.
   *
   * @param integerEncoding This is the integer encoding of the QoS policy which
   *   is to be used for determining the QoS policy type.
   * @return Returns the enumeration for the QoS policy type that matches the
   *   integer encoding, or a null reference if no matching QoS policy type is
   *   found.
   */
  public static QosPolicyType getQosPolicyType(final Integer integerEncoding) {
    if (integerEncoding == null) {
      return null;
    }
    return encodingMap.get(integerEncoding);
  }

  /**
   * Accesses the integer encoding value used to represent the QoS policy type.
   *
   * @return Returns the integer value which represents the QoS policy type.
   */
  public int getIntegerEncoding() {
    return integerEncoding;
  }

  /*
   * Default constructor generates the enumeration parameters from the supplied
   * constants.
   */
  private QosPolicyType(final int integerEncoding) {
    this.integerEncoding = integerEncoding;
  }

  /*
   * Static constructor builds the encoding map on startup.
   */
  static {
    encodingMap = new TreeMap<Integer, QosPolicyType>();
    for (final QosPolicyType qosPolicyType : EnumSet.allOf(QosPolicyType.class)) {
      final Integer key = qosPolicyType.getIntegerEncoding();
      if (encodingMap.containsKey(key)) {
        throw new RuntimeException("Duplicate QoS policy type encodings detected.");
      }
      encodingMap.put(key, qosPolicyType);
    }
  };
}
