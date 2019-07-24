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

import java.util.List;

import com.zynaptic.reaction.Deferred;
import com.zynaptic.reaction.Signal;

/**
 * Defines the MQTT client interface for accessing subscribed MQTT topics.
 *
 * @author Chris Holgate
 */
public interface MqttClientSubscriber {

  /**
   * Accesses an unmodifiable list of topic filters associated with the
   * subscriber. This will be the set of topics provided when creating the
   * subscriber instance.
   *
   * @return Returns an unmodifiable list of topic filters associated with the
   *   subscriber.
   */
  public List<TopicFilter> getTopicFilterList();

  /**
   * Accesses the signal instance that may be used for accepting notifications of
   * received messages that match the subscriber topics.
   *
   * @return Returns a reference to the signal that is used to notify receipt of
   *   new messages.
   */
  public Signal<ReceivedMessage> getNotificationSignal();

  /**
   * Initiates a subscribe request handshake with the server in order to subscribe
   * to the topics specified in the topic filter list.
   *
   * @return Returns a deferred event object which will have its callbacks
   *   executed on completion. The callback parameter will be an unmodifiable list
   *   of QoS policies in the same order as the original topic filter list. Each
   *   QoS policy corresponds to the QoS policy selected by the server for the
   *   corresponding topic filter. A QoS policy of {@link QosPolicyType#INVALID}
   *   indicates that a subscription to the corresponding topic filter was
   *   rejected by the server.
   */
  public Deferred<List<QosPolicyType>> subscribeClient();

  /**
   * Initiates an unsubscribe request handshake with the server in order to
   * unsubscribe from the topics specified in the topic filter list.
   *
   * @return Returns a deferred event object which will have its callbacks
   *   executed on completion. The callback parameter will be a boolean value that
   *   will normally be set to 'true'.
   */
  public Deferred<Boolean> unsubscribeClient();

}
