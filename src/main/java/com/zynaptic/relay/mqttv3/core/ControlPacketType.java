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

package com.zynaptic.relay.mqttv3.core;

import java.util.EnumSet;
import java.util.Map;
import java.util.TreeMap;

/**
 * Defines the enumerated set of MQTT control packet types.
 *
 * @author Chris Holgate
 */
public enum ControlPacketType {

  /**
   * Client request to connect to server.
   */
  CONNECT(0x10, false, false, true, false),

  /**
   * Connect acknowledgement.
   */
  CONNACK(0x20, false, false, false, true),

  /**
   * Publish message.
   */
  PUBLISH(0x30, true, false, true, true),

  /**
   * Publish acknowledgement.
   */
  PUBACK(0x40, false, true, true, true),

  /**
   * Publish received (assured delivery part 1).
   */
  PUBREC(0x50, false, true, true, true),

  /**
   * Publish release (assured delivery part 2).
   */
  PUBREL(0x62, false, true, true, true),

  /**
   * Publish complete (assured delivery part 3).
   */
  PUBCOMP(0x70, false, true, true, true),

  /**
   * Client subscribe request.
   */
  SUBSCRIBE(0x82, false, true, true, false),

  /**
   * Subscribe acknowledgement.
   */
  SUBACK(0x90, false, true, false, true),

  /**
   * Client unsubscribe request.
   */
  UNSUBSCRIBE(0xA2, false, true, true, false),

  /**
   * Unsubscribe acknowledgement.
   */
  UNSUBACK(0xB0, false, true, false, true),

  /**
   * Ping request.
   */
  PINGREQ(0xC0, false, false, true, false),

  /**
   * Ping response.
   */
  PINGRESP(0xD0, false, false, false, true),

  /**
   * Client disconnect request.
   */
  DISCONNECT(0xE0, false, false, true, false);

  // Specify the mapping of integer encoding values to control packet types.
  private static final Map<Integer, ControlPacketType> encodingMap;

  // Specify the integer encoding for a given enumeration.
  private final int integerEncoding;

  // Specify whether the flags in the header are used by the control packet.
  private final boolean useHeaderFlags;

  // Specify whether the packet identifier is required by the control packet.
  private final boolean requirePacketId;

  // Specify whether the control packet can be transmitted by the client.
  private final boolean clientInitiated;

  // Specify whether the control packet can be transmitted by the server.
  private final boolean serverInitiated;

  /**
   * Determines the control packet type given the integer encoding of the header
   * byte. Note that this includes matching on the control header flags field for
   * those messages where the flags have a fixed reserved value.
   *
   * @param integerEncoding This is the integer encoding of the header byte which
   *   is to be used for determining the control packet type.
   * @return Returns the enumeration for the control packet type that matches the
   *   integer encoding of the header byte, or a null reference if no matching
   *   control packet type is found.
   */
  public static ControlPacketType getControlPacketType(final Integer integerEncoding) {
    if (integerEncoding == null) {
      return null;
    }

    // Check for packet type using full header byte encoding.
    ControlPacketType packetType = encodingMap.get(integerEncoding);

    // Check for packet type after masking out the header flag bits. This will only
    // match for control packet types that use header flags.
    if (packetType == null) {
      packetType = encodingMap.get(integerEncoding & 0xF0);
      if ((packetType != null) && !packetType.usesHeaderFlags()) {
        packetType = null;
      }
    }
    return packetType;
  }

  /**
   * Accesses the integer encoding value used to represent the control packet type
   * in the packet header. This includes the control packet type (upper nibble)
   * and where appropriate the reserved flag bit settings (lower nibble).
   *
   * @return Returns the integer value which represents the control packet type.
   */
  public int getIntegerEncoding() {
    return integerEncoding;
  }

  /**
   * Indicates whether the control packet type uses the lower nibble of the header
   * byte as a set of header flags. This is only applicable to the publish packet
   * type.
   *
   * @return Returns a boolean value which will be set to 'true' if control
   *   packets of this type use the lower nibble of the header byte as a set of
   *   header flags and 'false' otherwise.
   */
  public boolean usesHeaderFlags() {
    return useHeaderFlags;
  }

  /**
   * Indicates whether the presence of a packet identifier is mandatory for the
   * control packet type.
   *
   * @return Returns a boolean value which will be set to 'true' if packet
   *   identifiers are mandatory for control packets of this type and 'false'
   *   otherwise.
   */
  public boolean requiresPacketId() {
    return requirePacketId;
  }

  /**
   * Indicates whether the control packet type can be used for packets transmitted
   * by the client.
   *
   * @return Returns a boolean value which will be set to 'true' if control
   *   packets of this type can be transmitted by the client and 'false'
   *   otherwise.
   */
  public boolean isClientInitiated() {
    return clientInitiated;
  }

  /**
   * Indicates whether the control packet type cane be used for packets
   * transmitted by the server.
   *
   * @return Returns a boolean value which will be set to 'true' if control
   *   packets of this type can be transmitted by the server and 'false'
   *   otherwise.
   */
  public boolean isServerInitiated() {
    return serverInitiated;
  }

  /*
   * Default constructor generates the enumeration parameters from the supplied
   * constants.
   */
  private ControlPacketType(final int integerEncoding, final boolean useHeaderFlags, final boolean requirePacketId,
      final boolean clientInitiated, final boolean serverInitiated) {
    this.integerEncoding = integerEncoding;
    this.useHeaderFlags = useHeaderFlags;
    this.requirePacketId = requirePacketId;
    this.clientInitiated = clientInitiated;
    this.serverInitiated = serverInitiated;
  }

  /*
   * Static constructor builds the encoding map on startup.
   */
  static {
    encodingMap = new TreeMap<Integer, ControlPacketType>();
    for (final ControlPacketType packetType : EnumSet.allOf(ControlPacketType.class)) {
      final Integer key = packetType.getIntegerEncoding();
      if (encodingMap.containsKey(key)) {
        throw new RuntimeException("Duplicate packet type encodings detected.");
      }
      encodingMap.put(key, packetType);
    }
  };
}
