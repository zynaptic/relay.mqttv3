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

import java.nio.charset.CharacterCodingException;

/**
 * Defines the interface for accessing the various fields of a received MQTT
 * message.
 *
 * @author Chris Holgate
 */
public interface ReceivedMessage {

  /**
   * Accesses the topic name associated with the received message.
   *
   * @return Returns the topic name for the received message.
   */
  public String getTopicName();

  /**
   * Accesses the index for the first topic filter that matched the topic name.
   * This may be used to identify the corresponding filter in the list returned by
   * {@link MqttClientSubscriber#getTopicFilterList()}.
   *
   * @return Returns the index for the first matching topic filter in the
   *   subscriber topic filter list.
   */
  public int getTopicFilterIndex();

  /**
   * Accesses the QoS policy associated with the received message.
   *
   * @return Returns the QoS policy for the received message.
   */
  public QosPolicyType getQosPolicy();

  /**
   * Accesses the message payload data as a raw byte array.
   *
   * @return Returns a byte array which contains the message payload data.
   */
  public byte[] getPayloadData();

  /**
   * Accesses the message payload data as a UTF-8 encoded string. This method may
   * be used when the specified topic is known to publish data in a string format.
   *
   * @return Returns a string containing the decoded message payload data.
   * @throws CharacterCodingException This exception will be thrown if the payload
   *   data is not a valid UTF-8 encoded string.
   */
  public String getPayloadString() throws CharacterCodingException;

}
