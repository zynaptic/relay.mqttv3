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
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;

import com.zynaptic.relay.mqttv3.QosPolicyType;
import com.zynaptic.relay.mqttv3.ReceivedMessage;

/**
 * Extends the basic control packet class in order to support fields specific to
 * publish packets.
 *
 * @author Chris Holgate
 */
class PublishPacket extends ControlPacket implements ReceivedMessage {

  private boolean duplicate;
  private final boolean retain;
  private final QosPolicyType qosPolicy;
  private final ValidatedString topicName;
  private final byte[] payload;
  private ValidatedString payloadString = null;
  private int topicFilterIndex = 0;

  /**
   * Provides protected constructor for message publish packets.
   *
   * @param topicName This is the topic to be used for publishing the new message.
   * @param payload This is the message payload to be published.
   * @param packetId This is the packet identifier to be used for the QoS
   *   handshake. For QoS 0 (at most once delivery) a null reference may be used.
   * @param duplicate This is a boolean flag which when set indicates that message
   *   delivery is being retried.
   * @param retain This is a boolean flag which when set indicates that the
   *   message should be treated as a retained message by the server.
   * @param qosPolicy This is the QoS policy to be used when delivering the
   *   published message.
   */
  PublishPacket(final ValidatedString topicName, final byte[] payload, final Short packetId, final boolean duplicate,
      final boolean retain, final QosPolicyType qosPolicy) {
    super(ControlPacketType.PUBLISH, packetId);
    this.topicName = topicName;
    this.payload = payload;
    this.duplicate = duplicate;
    this.retain = retain;
    this.qosPolicy = qosPolicy;
  }

  /**
   * Static method for creating a new publish packet instance given a packet body
   * for parsing. This may only be used for parsing PUBLISH packets.
   *
   * @param headerByte This is the header byte as extracted from the packet fixed
   *   header.
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
  static PublishPacket parsePacket(final int headerByte, final int messageLength, final ByteBuffer messageBuffer)
      throws MalformedPacketException {
    final int startPosition = messageBuffer.position();
    try {

      // Extract the flags from the header byte.
      final QosPolicyType qosPolicyField = QosPolicyType.getQosPolicyType((headerByte & 0x06) >> 1);
      if (qosPolicyField == null) {
        messageBuffer.position(startPosition + messageLength);
        throw new MalformedPacketException("Invalid QoS value in publish packet header flags");
      }
      final boolean duplicateFlag = (headerByte & 0x08) != 0;
      final boolean retainFlag = (headerByte & 0x01) != 0;

      // Extract the topic name field and packet identifier.
      final ValidatedString topicNameField = ValidatedString.create(messageBuffer);
      int variableHeaderLength = topicNameField.getByteEncoding().length;
      Short packetIdField = null;
      if (qosPolicyField != QosPolicyType.AT_MOST_ONCE) {
        packetIdField = messageBuffer.getShort();
        variableHeaderLength += 2;
      }

      // Extract the payload data, checking for invalid packet formatting.
      final int payloadLength = messageLength - variableHeaderLength;
      if (payloadLength < 0) {
        messageBuffer.position(startPosition + messageLength);
        throw new MalformedPacketException("Invalid variable length header in publish packet");
      }
      final byte[] payloadField = new byte[payloadLength];
      messageBuffer.get(payloadField);

      // Create the new publish packet.
      return new PublishPacket(topicNameField, payloadField, packetIdField, duplicateFlag, retainFlag, qosPolicyField);
    }

    // Treat any generated exceptions as malformed packet exceptions.
    catch (final Exception error) {
      messageBuffer.position(startPosition + messageLength);
      throw new MalformedPacketException("Publish packet parsing failed", error);
    }
  }

  /*
   * Implements ReceivedMessage.getTopicName()
   */
  @Override
  public String getTopicName() {
    return topicName.getString();
  }

  /*
   * Implements ReceivedMessage.getTopicFilterIndex()
   */
  @Override
  public synchronized int getTopicFilterIndex() {
    return topicFilterIndex;
  }

  /*
   * Implements ReceivedMessage.getQosPolicy()
   */
  @Override
  public QosPolicyType getQosPolicy() {
    return qosPolicy;
  }

  /*
   * Implements ReceivedMessage.getPayloadData()
   */
  @Override
  public byte[] getPayloadData() {
    return payload;
  }

  /*
   * Implements ReceivedMessage.getPayloadString()
   */
  @Override
  public synchronized String getPayloadString() throws CharacterCodingException {
    if (payloadString == null) {
      payloadString = ValidatedString.create(payload);
    }
    return payloadString.getString();
  }

  /**
   * Assigns the topic filter index associated with the received message.
   *
   * @param topicFilterIndex This is the index of the first subscriber topic
   *   filter that matched the topic name of the received message.
   */
  synchronized void setTopicFilterIndex(final int topicFilterIndex) {
    this.topicFilterIndex = topicFilterIndex;
  }

  /**
   * Assigns the duplicate flag value associated with the published message.
   *
   * @param duplicate This is a boolean value which reflects the required state of
   *   the duplicate flag.
   */
  synchronized void setDuplicate(final boolean duplicate) {
    this.duplicate = duplicate;
  }

  /**
   * Accesses the duplicate flag value associated with the published message.
   *
   * @return Returns the duplicate flag value for the published message.
   */
  synchronized boolean isDuplicate() {
    return duplicate;
  }

  /*
   * Overrides ControlPacket.formatPacket()
   */
  @Override
  synchronized ByteBuffer formatPacket() {

    // Combine the control packet type with the option flags.
    int headerByte = getControlPacketType().getIntegerEncoding();
    headerByte |= (duplicate) ? 0x08 : 0x00;
    headerByte |= (retain) ? 0x01 : 0x00;
    headerByte |= qosPolicy.getIntegerEncoding() << 1;

    // Create the extended header fields.
    final byte[] topicNameBytes = topicName.getByteEncoding();
    int messageLength = payload.length + topicNameBytes.length;
    if (qosPolicy != QosPolicyType.AT_MOST_ONCE) {
      messageLength += 2;
    }

    // Assemble the message into a byte buffer.
    final ByteBuffer outputBuffer = ByteBuffer.allocate(messageLength + 5);
    outputBuffer.put((byte) headerByte);
    outputBuffer.put(VarLenQuantity.create(messageLength).getByteEncoding());
    outputBuffer.put(topicNameBytes);
    if (qosPolicy != QosPolicyType.AT_MOST_ONCE) {
      outputBuffer.putShort(getPacketId().shortValue());
    }
    outputBuffer.put(payload);
    return outputBuffer.flip();
  }

  /*
   * Overrides ControlPacket.tracePacket(...)
   */
  @Override
  synchronized String tracePacket(final boolean hasTextPayload, final boolean verbose) {
    final StringBuilder stringBuilder = new StringBuilder(String.format("%-11s ID: %04X, QS: %s, DP: %b, RT: %b",
        getControlPacketType().toString(), getPacketId(), qosPolicy.toString(), duplicate, retain));
    if (verbose) {
      stringBuilder.append("\n    Topic name   : ").append(topicName.getString());
      if (hasTextPayload) {
        stringBuilder.append("\n    Text payload : ").append(new String(payload, StandardCharsets.UTF_8));
      }
    }
    return stringBuilder.toString();
  }
}
