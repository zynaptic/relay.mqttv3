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

/**
 * Provides the base class implementation of MQTT control packets. This may be
 * used directly to implement PUBACK, PUBREC, PUBREL, PUBCOMP, UNSUBACK,
 * PINGREQ, PINGRESP and DISCONNECT packets.
 *
 * @author Chris Holgate
 */
class ControlPacket {

  private final ControlPacketType controlPacketType;
  private final Short packetId;

  /**
   * Provides protected constructor for common control packets.
   *
   * @param controlPacketType This is the type of the control packet, which may be
   *   one of PUBACK, PUBREC, PUBREL, PUBCOMP, UNSUBACK, PINGREQ, PINGRESP and
   *   DISCONNECT.
   * @param packetId This is the packet identifier. For packet types that support
   *   packet identifiers it should be a valid short integer value, otherwise it
   *   should be a null reference.
   */
  ControlPacket(final ControlPacketType controlPacketType, final Short packetId) {
    this.controlPacketType = controlPacketType;
    this.packetId = packetId;
  }

  /**
   * Static method for creating a new control packet instance given a packet body
   * for parsing. This may be used for parsing PUBACK, PUBREC, PUBREL, PUBCOMP,
   * UNSUBACK, PINGREQ, PINGRESP and DISCONNECT packets.
   *
   * @param ControlPacketType controlPacketType This is the type of the control
   *   packet, as derived from the first header byte.
   * @param messageLength This is the remaining length field as extracted from the
   *   packet fixed header.
   * @param messageBuffer This a message buffer which is guaranteed to contain the
   *   remainder of the control packet, as determined by the messageLength
   *   parameter. The position identifier refers to the byte immediately following
   *   the fixed header.
   * @return Returns a newly constructed connection acknowledgement packet
   *   instance.
   * @throws MalformedPacketException This exception will be thrown if the packet
   *   format does not conform to the specification.
   */
  static ControlPacket parsePacket(final ControlPacketType controlPacketType, final int messageLength,
      final ByteBuffer messageBuffer) throws MalformedPacketException {
    final int startPosition = messageBuffer.position();

    // Parse basic control packets with packet ID field.
    if (controlPacketType.requiresPacketId()) {
      if (messageLength != 2) {
        messageBuffer.position(startPosition + messageLength);
        throw new MalformedPacketException("Invalid fixed packet length");
      }
      final short packetIdField = messageBuffer.getShort();
      return new ControlPacket(controlPacketType, packetIdField);
    }

    // Parse basic control packets without packet ID field.
    else {
      if (messageLength != 0) {
        messageBuffer.position(startPosition + messageLength);
        throw new MalformedPacketException("Invalid fixed packet length");
      }
      return new ControlPacket(controlPacketType, null);
    }
  }

  /**
   * Accesses the control packet type which is associated with the control packet
   * instance.
   *
   * @return Returns the control packet type.
   */
  final ControlPacketType getControlPacketType() {
    return controlPacketType;
  }

  /**
   * Accesses the control packet identifier which is associated with the control
   * packet instance.
   *
   * @return Returns the control packet identifier, or a null reference if the
   *   specific control packet type does not support packet identifiers.
   */
  final Short getPacketId() {
    return packetId;
  }

  /**
   * Formats the control packet for transmission over the transport layer link.
   * This should be overridden in order to support the more complex control packet
   * formats.
   *
   * @return Returns a newly created byte buffer that contains the formatted
   *   control packet.
   */
  ByteBuffer formatPacket() {

    // Assemble the message into a byte buffer.
    final ByteBuffer outputBuffer = ByteBuffer.allocate(4);
    outputBuffer.put((byte) getControlPacketType().getIntegerEncoding());
    if (controlPacketType.requiresPacketId()) {
      outputBuffer.put((byte) 2);
      outputBuffer.putShort(packetId.shortValue());
    } else {
      outputBuffer.put((byte) 0);
    }
    return outputBuffer.flip();
  }

  /**
   * Formats the control packet information for tracing and logging purposes. This
   * should be overridden in order to support the more complex packet formats.
   *
   * @param hasTextPayload this is a boolean flag which should be set to 'true' if
   *   all the packet fields that are specified as byte arrays should be
   *   interpreted as UTF-8 encoded strings.
   * @param verbose This is a boolean flag which should be set to 'true' if
   *   additional packet information such as payload data should be included in
   *   the trace.
   * @return Returns a string that contains the formatted control packet
   *   information.
   */
  String tracePacket(final boolean hasTextPayload, final boolean verbose) {
    if (packetId != null) {
      return String.format("%-11s ID: %04X", controlPacketType.toString(), packetId);
    } else {
      return String.format("%-11s", controlPacketType.toString());
    }
  }
}
