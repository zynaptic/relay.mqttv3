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
import java.util.List;

import com.zynaptic.relay.mqttv3.QosPolicyType;
import com.zynaptic.relay.mqttv3.TopicFilter;

/**
 * Extends the basic control packet class in order to support fields specific to
 * subscription request packets of types SUBSCRIBE and UNSUBSCRIBE.
 *
 * @author Chris Holgate
 */
final class SubscribePacket extends ControlPacket {

  private final List<TopicFilter> topicFilterList;

  /**
   * Provides protected constructor for subscription request packets.
   *
   * @param controlPacketType This is the type of the subscription request packet
   *   which may be one of SUBSCRIBE or UNSUBSCRIBE.
   * @param packetId This is the packet identifier that is used to match
   *   subscription requests to their corresponding acknowledgement packets.
   * @param topicFilterList This is a list of topic filters which are being used
   *   to subscribe to or unsubscribe from server topics.
   */
  SubscribePacket(final ControlPacketType controlPacketType, final short packetId,
      final List<TopicFilter> topicFilterList) {
    super(controlPacketType, packetId);
    this.topicFilterList = topicFilterList;
  }

  /**
   * Static method for creating a new subscribe packet instance given a packet
   * body for parsing. This may only be used for parsing SUBSCRIBE or UNSUBSCRIBE
   * packets.
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
  static SubscribePacket parsePacket(final ControlPacketType controlPacketType, final int messageLength,
      final ByteBuffer messageBuffer) throws MalformedPacketException {
    final int startPosition = messageBuffer.position();
    try {
      final short packetIdField = messageBuffer.getShort();
      final LinkedList<TopicFilter> filterFields = new LinkedList<TopicFilter>();

      // Repeatedly read in the filter fields until the total number of bytes consumed
      // equals the message length.
      while (messageBuffer.position() - startPosition != messageLength) {
        final ValidatedString topicString = ValidatedString.create(messageBuffer);
        final QosPolicyType topicQosPolicy = null;
        if (controlPacketType == ControlPacketType.SUBSCRIBE) {
          QosPolicyType.getQosPolicyType((int) messageBuffer.get());
          if (topicQosPolicy == null) {
            messageBuffer.position(startPosition + messageLength);
            throw new MalformedPacketException("Topic filter QoS level is not valid");
          }
        }
        if (messageBuffer.position() - startPosition > messageLength) {
          messageBuffer.position(startPosition + messageLength);
          throw new MalformedPacketException("Payload data format does not match declared packet length");
        }
        filterFields.add(new TopicFilterCore(topicString.getString(), topicQosPolicy));
      }

      // Check for missing filter fields - this is a protocol violation.
      if (filterFields.isEmpty()) {
        messageBuffer.position(startPosition + messageLength);
        throw new MalformedPacketException("Missing payload data");
      }

      // Create the new subscribe packet.
      return new SubscribePacket(controlPacketType, packetIdField, filterFields);
    }

    // Treat any generated exceptions as malformed packet exceptions.
    catch (final Exception error) {
      messageBuffer.position(startPosition + messageLength);
      throw new MalformedPacketException("Subscribe packet parsing failed", error);
    }
  }

  /*
   * Overrides ControlPacket.formatPacket()
   */
  @Override
  public ByteBuffer formatPacket() {

    // Format the payload topic filters.
    final List<byte[]> topicFilterEncodings = new LinkedList<byte[]>();
    int messageLength = 2;
    for (final TopicFilter topicFilter : topicFilterList) {
      final byte[] topicFilterBytes = ((TopicFilterCore) topicFilter).getFilterByteEncoding();
      topicFilterEncodings.add(topicFilterBytes);
      messageLength += topicFilterBytes.length;
      if (getControlPacketType() == ControlPacketType.SUBSCRIBE) {
        topicFilterEncodings.add(new byte[] { (byte) topicFilter.getQosPolicy().getIntegerEncoding() });
        messageLength += 1;
      }
    }

    // Assemble the message into a byte buffer.
    final ByteBuffer outputBuffer = ByteBuffer.allocate(messageLength + 5);
    outputBuffer.put((byte) getControlPacketType().getIntegerEncoding());
    outputBuffer.put(VarLenQuantity.create(messageLength).getByteEncoding());
    outputBuffer.putShort(getPacketId());
    for (final byte[] topicFilterEncoding : topicFilterEncodings) {
      outputBuffer.put(topicFilterEncoding);
    }
    return outputBuffer.flip();
  }

  /*
   * Overrides ControlPacket.tracePacket(...)
   */
  @Override
  public String tracePacket(final boolean hasTextPayload, final boolean verbose) {
    final StringBuilder stringBuilder = new StringBuilder(
        String.format("%-11s ID: %04X", getControlPacketType().toString(), getPacketId()));
    if (verbose) {
      boolean firstElement = true;
      for (final TopicFilter topicFilter : topicFilterList) {
        if (firstElement) {
          firstElement = false;
          if (getControlPacketType() == ControlPacketType.SUBSCRIBE) {
            stringBuilder.append("\n    Subscribed topics   : [ ");
          } else {
            stringBuilder.append("\n    Unsubscribed topics : [ ");
          }
        } else {
          stringBuilder.append(",\n                            ");
        }
        stringBuilder.append(topicFilter.getFilterString().toString());
        if (getControlPacketType() == ControlPacketType.SUBSCRIBE) {
          stringBuilder.append(" : ").append(topicFilter.getQosPolicy().toString());
        }
      }
      stringBuilder.append(" ]");
    }
    return stringBuilder.toString();
  }
}
