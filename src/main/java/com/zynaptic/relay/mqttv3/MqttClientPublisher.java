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

import com.zynaptic.reaction.Deferred;

/**
 * Defines the MQTT client interface for accessing locally published MQTT
 * topics.
 *
 * @author Chris Holgate
 */
public interface MqttClientPublisher {

  /**
   * Accesses the topic name which is associated with the published topic. This
   * conforms to the standard MQTT requirements for topic names.
   *
   * @return Returns the topic name which is associated with the published topic.
   */
  public String getTopicName();

  /**
   * Accesses the quality of service policy which is associated with the published
   * topic.
   *
   * @return Returns the quality of service policy which is associated with the
   *   published topic.
   */
  public QosPolicyType getQosPolicy();

  /**
   * Transmits a new MQTT publish message to the server using the associated MQTT
   * topic name and quality of service policy.
   *
   * @param payload This is a byte array which contains the payload to be
   *   published to the MQTT server.
   * @param retain This is a boolean flag which will be inserted into the publish
   *   message header as the 'retain' flag. When set to 'true' this will cause the
   *   MQTT server to treat the published message as a retained message.
   * @return Returns a deferred event object which will have its callbacks
   *   executed once the message has been queued for transmission. The callback
   *   parameter is a boolean value which will normally be set to 'true'.
   */
  public Deferred<Boolean> publish(final byte[] payload, final boolean retain);

}
