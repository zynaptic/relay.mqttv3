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
import java.util.List;

import com.zynaptic.reaction.Deferrable;
import com.zynaptic.reaction.Deferred;
import com.zynaptic.reaction.Reactor;
import com.zynaptic.reaction.Signal;
import com.zynaptic.reaction.Signalable;
import com.zynaptic.relay.mqttv3.MqttClientSubscriber;
import com.zynaptic.relay.mqttv3.QosPolicyType;
import com.zynaptic.relay.mqttv3.ReceivedMessage;
import com.zynaptic.relay.mqttv3.TopicFilter;

/**
 * Implements the core functionality for MQTT subscribed topic handlers.
 *
 * @author Chris Holgate
 */
final class MqttClientSubscriberCore implements MqttClientSubscriber, Signalable<PublishPacket> {

  private final Reactor reactor;
  private final MqttClientConnectionCore clientConnection;
  private final List<TopicFilter> topicFilterList;
  private final Signal<ReceivedMessage> notificationSignal;

  /**
   * Protected constructor used by the client connection instance for creating new
   * subscribed topics.
   *
   * @param reactor This is the reactor service which is to be used for
   *   asynchronous event management.
   * @param clientConnection This is the client connection which instantiated the
   *   topic subscriber.
   * @param topicFilterList This is the list of topic filters that are associated
   *   with the topic subscriber.
   */
  MqttClientSubscriberCore(final Reactor reactor, final MqttClientConnectionCore clientConnection,
      final List<TopicFilter> topicFilterList) {
    this.reactor = reactor;
    this.clientConnection = clientConnection;
    this.topicFilterList = Collections.unmodifiableList(topicFilterList);
    notificationSignal = reactor.newSignal();
  }

  /*
   * Implements MqttClientSubscriber.getTopicFilterList()
   */
  @Override
  public List<TopicFilter> getTopicFilterList() {
    return topicFilterList;
  }

  /*
   * Implements MqttClientSubscriber.getNotificationSignal()
   */
  @Override
  public Signal<ReceivedMessage> getNotificationSignal() {
    return notificationSignal.makeRestricted();
  }

  /*
   * Implements MqttClientSubscriber.subscribeClient()
   */
  @Override
  public Deferred<List<QosPolicyType>> subscribeClient() {
    final short packetId = clientConnection.getSendHandshakeStates().getAvailablePacketId();
    final ControlPacket txControlPacket = new SubscribePacket(ControlPacketType.SUBSCRIBE, packetId, topicFilterList);

    // Store the subscribe packet data for retransmission if required.
    final ByteBuffer subscribeBuffer = txControlPacket.formatPacket();
    final byte[] subscribeData = new byte[subscribeBuffer.remaining()];
    subscribeBuffer.get(subscribeData);

    final Deferred<ControlPacket> deferredResponse = reactor.newDeferred();
    clientConnection.getSendHandshakeStates().insert(packetId, subscribeData, HandshakeState.SUBSCRIBE_REQUEST)
        .addDeferrable(new DeferredPacketSend(deferredResponse, txControlPacket), true);
    return deferredResponse.addCallback(x -> ((SubackPacket) x).getReturnCodeList()).makeRestricted();
  }

  /*
   * Implements MqttClientSubscriber.unsubscribeClient()
   */
  @Override
  public Deferred<Boolean> unsubscribeClient() {
    final short packetId = clientConnection.getSendHandshakeStates().getAvailablePacketId();
    final ControlPacket txControlPacket = new SubscribePacket(ControlPacketType.UNSUBSCRIBE, packetId, topicFilterList);

    // Store the unsubscribe packet data for retransmission if required.
    final ByteBuffer subscribeBuffer = txControlPacket.formatPacket();
    final byte[] subscribeData = new byte[subscribeBuffer.remaining()];
    subscribeBuffer.get(subscribeData);

    final Deferred<ControlPacket> deferredResponse = reactor.newDeferred();
    clientConnection.getSendHandshakeStates().insert(packetId, subscribeData, HandshakeState.UNSUBSCRIBE_REQUEST)
        .addDeferrable(new DeferredPacketSend(deferredResponse, txControlPacket), true);
    return deferredResponse.addCallback(x -> true).makeRestricted();
  }

  /*
   * The signalable callback is used to check inbound messages against the topic
   * filters and then forward any matching messages to the notification signal
   * subscribers.
   */
  @Override
  public void onSignal(final Signal<PublishPacket> signalId, final PublishPacket publishPacket) {
    if (publishPacket == null) {
      notificationSignal.signalFinalize(null);
    } else {
      int filterIndex = 0;
      for (final TopicFilter topicFilter : topicFilterList) {
        if (((TopicFilterCore) topicFilter).matches(publishPacket.getTopicName())) {
          publishPacket.setTopicFilterIndex(filterIndex);
          notificationSignal.signal(publishPacket);
          break;
        }
        filterIndex++;
      }
    }
  }

  /*
   * Deferred callback handler that transmits a subscribe or unsubscribe packet
   * once the handshake state table has been updated. This also attaches the
   * deferred callback object associated with the original request to the
   * handshake state table entry so that it can be triggered on receipt of an
   * acknowledgement packet.
   */
  private class DeferredPacketSend implements Deferrable<HandshakeInfo, Void> {
    private final Deferred<ControlPacket> deferredResponse;
    private final ControlPacket txControlPacket;

    private DeferredPacketSend(final Deferred<ControlPacket> deferredResponse, final ControlPacket txControlPacket) {
      this.deferredResponse = deferredResponse;
      this.txControlPacket = txControlPacket;
    }

    @Override
    public Void onCallback(final Deferred<HandshakeInfo> deferred, final HandshakeInfo handshake) {
      handshake.setDeferredResponse(deferredResponse);
      clientConnection.getTransmitQueue().transmitPacket(txControlPacket);
      return null;
    }

    @Override
    public Void onErrback(final Deferred<HandshakeInfo> deferred, final Exception error) {
      deferredResponse.errback(error);
      return null;
    }
  }
}
