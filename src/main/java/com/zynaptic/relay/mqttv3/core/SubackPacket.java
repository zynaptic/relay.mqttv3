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
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import com.zynaptic.relay.mqttv3.QosPolicyType;

/**
 * Extends the basic control packet class in order to support fields specific to
 * subscription acknowledgement packets.
 *
 * @author Chris Holgate
 */
final class SubackPacket extends ControlPacket {

  private final List<QosPolicyType> returnCodeList;

  /**
   * Provides protected constructor for subscription acknowledgement packets.
   *
   * @param packetId This is the packet identifier that is used to match
   *   subscription requests to their corresponding acknowledgement packets.
   * @param returnCodeList This is a list of return codes, where each return code
   *   corresponds to a topic subscription in the original subscription request.
   */
  SubackPacket(final short packetId, final List<QosPolicyType> returnCodeList) {
    super(ControlPacketType.SUBACK, packetId);
    this.returnCodeList = Collections.unmodifiableList(returnCodeList);
  }

  /**
   * Static method for creating a new subscribe packet instance given a packet
   * body for parsing. This may only be used for parsing SUBACK packets.
   *
   * @param messageLength This is the remaining length field as extracted from the
   *   packet fixed header.
   * @param messageBuffer This a message buffer which is guaranteed to contain the
   *   remainder of the control packet, as determined by the messageLength
   *   parameter. The position identifier refers to the byte immediately following
   *   the fixed header.
   * @return Returns a newly constructed subscribe packet instance.
   * @throws MalformedPacketException This exception will be thrown if the packet
   *   format does not conform to the specification.
   */
  static SubackPacket parsePacket(final int messageLength, final ByteBuffer messageBuffer)
      throws MalformedPacketException {
    final int startPosition = messageBuffer.position();

    // Check the minimum packet length. There must be a packet identifier and at
    // least one return code.
    if (messageLength <= 2) {
      messageBuffer.position(startPosition + messageLength);
      throw new MalformedPacketException("Invalid minimum packet length");
    }

    // Extract the packet ID field.
    final short packetIdField = messageBuffer.getShort();

    // Extract the return codes in the remaining message bytes.
    final LinkedList<QosPolicyType> returnCodes = new LinkedList<QosPolicyType>();
    while (messageBuffer.position() - startPosition != messageLength) {
      final QosPolicyType returnCode = QosPolicyType.getQosPolicyType(0xFF & messageBuffer.get());
      if (returnCode == null) {
        messageBuffer.position(startPosition + messageLength);
        throw new MalformedPacketException("Unsupported subscribe return code");
      }
      returnCodes.add(returnCode);
    }
    return new SubackPacket(packetIdField, returnCodes);
  }

  /**
   * Accesses an unmodifiable list of topic subscription return codes associated
   * with the subscription acknowledgement.
   *
   * @return Returns an unmodifiable list of topic subscription return codes in
   *   the same order as the original list of topic subscription requests.
   */
  List<QosPolicyType> getReturnCodeList() {
    return returnCodeList;
  }

  /*
   * Overrides ControlPacket.formatPacket()
   */
  @Override
  ByteBuffer formatPacket() {

    // Assemble the message into a byte buffer.
    final ByteBuffer outputBuffer = ByteBuffer.allocate(returnCodeList.size() + 7);
    outputBuffer.put((byte) getControlPacketType().getIntegerEncoding());
    outputBuffer.put(VarLenQuantity.create(returnCodeList.size() + 2).getByteEncoding());
    outputBuffer.putShort(getPacketId());

    // Add the return codes.
    for (final QosPolicyType qosReturnCode : returnCodeList) {
      outputBuffer.put((byte) qosReturnCode.getIntegerEncoding());
    }
    return outputBuffer.flip();
  }

  /*
   * Overrides ControlPacket.tracePacket(...)
   */
  @Override
  String tracePacket(final boolean hasTextPayload, final boolean verbose) {
    final StringBuilder stringBuilder = new StringBuilder(
        String.format("%-11s ID: %04X", getControlPacketType().toString(), getPacketId()));
    if (verbose) {
      boolean firstElement = true;
      for (final QosPolicyType returnCode : returnCodeList) {
        if (firstElement) {
          firstElement = false;
          stringBuilder.append("\n    Return codes : [ ");
        } else {
          stringBuilder.append(", ");
        }
        stringBuilder.append(returnCode.toString());
      }
      stringBuilder.append(" ]");
    }
    return stringBuilder.toString();
  }
}
