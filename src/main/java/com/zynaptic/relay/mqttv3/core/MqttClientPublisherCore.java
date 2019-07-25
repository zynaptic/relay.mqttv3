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

import com.zynaptic.reaction.Deferred;
import com.zynaptic.reaction.Reactor;
import com.zynaptic.relay.mqttv3.MqttClientPublisher;
import com.zynaptic.relay.mqttv3.QosPolicyType;

/**
 * Implements the core functionality for MQTT published topic handlers.
 *
 * @author Chris Holgate
 */
class MqttClientPublisherCore implements MqttClientPublisher {

  private final Reactor reactor;
  private final MqttClientConnectionCore clientConnection;
  private final ValidatedString topicName;
  private final QosPolicyType qosPolicy;

  /**
   * Protected constructor used by the client connection instance for creating new
   * published topics.
   *
   * @param reactor This is the reactor service which is to be used for
   *   asynchronous event management.
   * @param clientConnection This is the client connection which instantiated the
   *   topic publisher.
   * @param topicName This is the topic name for the published topic.
   * @param qosPolicy This is the QoS policy to be used by the published topic.
   */
  MqttClientPublisherCore(final Reactor reactor, final MqttClientConnectionCore clientConnection,
      final String topicName, final QosPolicyType qosPolicy) {
    this.reactor = reactor;
    this.clientConnection = clientConnection;
    try {
      this.topicName = ValidatedString.create(topicName);
    } catch (final CharacterCodingException error) {
      throw new IllegalArgumentException("Topic name string includes invalid characters", error);
    }
    this.qosPolicy = qosPolicy;
    if (!this.topicName.isValidTopicName()) {
      throw new IllegalArgumentException("Wildcard characters '+' and '#' are not permitted in topic names");
    }
  }

  /*
   * Implements MqttClientPublisher.getTopicName()
   */
  @Override
  public String getTopicName() {
    return topicName.getString();
  }

  /*
   * Implements MqttClientPublisher.getQosPolicy()
   */
  @Override
  public QosPolicyType getQosPolicy() {
    return qosPolicy;
  }

  /*
   * Implements MqttClientPublisher.publish(...)
   */
  @Override
  public Deferred<Boolean> publish(final byte[] payload, final boolean retain) {

    // Store the publish packet for QoS 1 or QoS 2 delivery and then append the
    // packet to the transmit queue on completion.
    if ((qosPolicy == QosPolicyType.AT_LEAST_ONCE) || (qosPolicy == QosPolicyType.EXACTLY_ONCE)) {
      final short packetId = clientConnection.getSendHandshakeStates().getAvailablePacketId();
      final PublishPacket publishPacket = new PublishPacket(topicName, payload, packetId, false, retain, qosPolicy);

      // The stored packet data has the duplicate flag set ready for retransmission if
      // required.
      publishPacket.setDuplicate(true);
      final ByteBuffer publishBuffer = publishPacket.formatPacket();
      final byte[] publishData = new byte[publishBuffer.remaining()];
      publishBuffer.get(publishData);

      // The packet data for transmission has the duplicate flag cleared.
      publishPacket.setDuplicate(false);
      final HandshakeState handshakeState = (qosPolicy == QosPolicyType.AT_LEAST_ONCE) ? HandshakeState.QOS1_TX_PUBLISH
          : HandshakeState.QOS2_TX_PUBLISH;
      final Deferred<HandshakeInfo> deferredStateUpdate = clientConnection.getSendHandshakeStates().insert(packetId,
          publishData, handshakeState);
      return deferredStateUpdate.addCallback(x -> {
        clientConnection.getTransmitQueue().transmitPacket(publishPacket);
        return true;
      });
    }

    // Immediately transmit the publish packet for QoS 0.
    else {
      final PublishPacket publishPacket = new PublishPacket(topicName, payload, null, false, retain,
          QosPolicyType.AT_MOST_ONCE);
      clientConnection.getTransmitQueue().transmitPacket(publishPacket);
      return reactor.callDeferred(true);
    }
  }
}
