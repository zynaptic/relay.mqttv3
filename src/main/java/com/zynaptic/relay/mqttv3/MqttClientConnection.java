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

/**
 * Defines the MQTT client connection interface which is used to manage the
 * client connection to a single MQTT server.
 *
 * @author Chris Holgate
 */
public interface MqttClientConnection {

  /**
   * Accesses the connection status associated with the MQTT client connection.
   * The client connection will only be usable if the connection status has been
   * set to {@link ConnectionStatus#ACCEPTED}.
   *
   * @return Returns the MQTT connection status associated with this client
   *   connection.
   */
  public ConnectionStatus getConnectionStatus();

  /**
   * Creates a new topic publisher instance for use with the underlying client
   * connection. This may then be used to send MQTT publish messages to the
   * server.
   *
   * @param topicName This is the topic name which is to be used for the published
   *   messages. It should conform to the standard MQTT topic naming convention.
   * @param qosPolicy This is the quality of service policy to be used when
   *   sending published messages to the server.
   * @return Returns a topic publisher instance which may be used for sending
   *   publish messages to the server.
   */
  public MqttClientPublisher createPublisher(final String topicName, final QosPolicyType qosPolicy);

  /**
   * Creates a new topic filter instance for use when subscribing to new server
   * topics.
   *
   * @param filterPattern This is a string which should contain a valid filter
   *   pattern, as defined by the MQTT specification.
   * @param qosPolicy This is the QoS policy which should be used for transferring
   *   subscriber messages.
   * @return Returns a new topic filter instance for use when subscribing to new
   *   server topics.
   */
  public TopicFilter createTopicFilter(String filterPattern, QosPolicyType qosPolicy);

  /**
   * Creates a new topic subscriber instance for use with the underlying client
   * connection. This may then be used to receive all MQTT publish messages from
   * the server which match the specified topic filter.
   *
   * @param topicFilter This is the topic filter which is to be used when
   *   selecting the messages to be received via the subscribed topic instance.
   * @return Returns a topic subscriber instance which may be used for receiving
   *   published messages from the server.
   */
  public MqttClientSubscriber createSubscriber(final TopicFilter topicFilter);

  /**
   * Creates a new topic subscriber instance for use with the underlying client
   * connection. This may then be used to receive all MQTT publish messages from
   * the server which match at least one of the specified topic filters.
   *
   * @param topicFilterList This is the list of topic filters which are to be used
   *   when selecting the messages to be received via the subscribed topic
   *   instance.
   * @return Returns a topic subscriber instance which may be used for receiving
   *   published messages from the server.
   */
  public MqttClientSubscriber createSubscriber(final List<TopicFilter> topicFilterList);

  /**
   * Start packet dispatch processing. In order to avoid dropping subscribed
   * messages this should be called once all subscribers have been created for the
   * connection.
   */
  public void startPacketDispatch();

}
