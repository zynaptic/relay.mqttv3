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

/**
 * Defines the interface for accessing the various fields of an MQTT topic
 * filter and performing topic filter matches.
 *
 * @author Chris Holgate
 */
public interface TopicFilter {

  /**
   * Accesses the MQTT filter string which is used to select matching topics.
   *
   * @return Returns the MQTT topic filter string.
   */
  public String getFilterString();

  /**
   * Accesses the MQTT filter regular expression which is used to select matching
   * topics.
   *
   * @return Returns a regular expression definition which may be used for
   *   matching MQTT topic strings.
   */
  public String getFilterRegex();

  /**
   * Accesses the QoS policy which is associated with the topic filter. This is
   * the maximum QoS level which would be supported by the client for messages
   * matching the topic filter.
   *
   * @return Returns the QoS policy for the topic filter.
   */
  public QosPolicyType getQosPolicy();

  /**
   * Performs regular expression matching on a given topic name to determine if it
   * matches the MQTT topic filter.
   *
   * @param topicName This is the topic name which is to be matched against the
   *   MQTT topic filter.
   * @return Returns a boolean value which will be set to 'true' if the specified
   *   topic name matches the topic filter and 'false' otherwise.
   */
  public boolean matches(String topicName);

}
