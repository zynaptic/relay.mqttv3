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
 * Defines the public interface to connection parameter sets.
 *
 * @author Chris Holgate
 */
public interface ConnectionParameters {

  /**
   * Assigns new user credentials (user name and password) to the connection
   * parameter set. By default a newly created parameter set will have no user
   * credentials.
   *
   * @param userName This is the user name to be used when establishing the
   *   connection. It should be a string that conforms to the requirements of the
   *   MQTT specification or a null reference to imply that no user credentials
   *   are to be provided.
   * @param userPassword This is the user password to be used when establishing
   *   the connection. It should be a byte array of no more than 65535 bytes in
   *   length or a null reference to imply that no user password is present. It is
   *   only valid if a non-null user name parameter is specified.
   * @return Returns a reference to this connection parameter set object, allowing
   *   fluent assignment of additional parameters.
   */
  public ConnectionParameters setUserCredentials(String userName, byte[] userPassword);

  /**
   * Assigns new will topic parameters to the connection parameter set. By default
   * a newly created parameter set will have no will topic.
   *
   * @param willTopic This is the will topic to be assigned when establishing the
   *   connection. It should be a string that conforms the the MQTT topic name
   *   requirements or a null reference to imply that no will topic is to be
   *   assigned.
   * @param willMessage This is the will message to be assigned when establishing
   *   the connection. It should be a byte array of no more than 65535 bytes in
   *   length or a null reference to imply that no will message is to be assigned.
   * @param willRetain This is the will message retain flag to be assigned when
   *   establishing the connection. It is only valid if the will topic is
   *   specified.
   * @param willQosPolicy This is the will QoS policy to be assigned when
   *   establishing the connection. It is only valid if the will topic is
   *   specified.
   * @return Returns a reference to this connection parameter set object, allowing
   *   fluent assignment of additional parameters.
   */
  public ConnectionParameters setWillParameters(String willTopic, byte[] willMessage, boolean willRetain,
      QosPolicyType willQosPolicy);

  /**
   * Assigns new timing parameters to the connection parameter set. By default a
   * newly created parameter set will have a keep alive time of 15 minutes and a
   * transmit interval of 2.5 seconds.
   *
   * @param keepAliveTime This is the keep alive time to be used when establishing
   *   the connection. It should be an integer value between 1 and 65535 seconds.
   *   A value of zero may also be used to disable ping requests and keep alive
   *   timeouts.
   * @param transmitInterval This is the minimum interval between transmitted
   *   transport layer packets, expressed as an integer number of milliseconds. If
   *   required, multiple MQTT packets are concatenated into a single transport
   *   layer packet in order to ensure that the transmit interval is observed.
   * @return Returns a reference to this connection parameter set object, allowing
   *   fluent assignment of additional parameters.
   */
  public ConnectionParameters setTimingParameters(int keepAliveTime, int transmitInterval);

  /**
   * Accesses the client identifier for the connection parameter set.
   *
   * @return Returns the client identifier to be used when establishing the
   *   connection.
   */
  public String getClientIdentifier();

  /**
   * Accesses the user name for the connection parameter set.
   *
   * @return Returns the user name to be used when establishing the connection, or
   *   a null reference if no user name has been specified.
   */
  public String getUserName();

  /**
   * Accesses the user password for the connection parameter set.
   *
   * @return Returns the user password to be used when establishing the
   *   connection, or a null reference if no user password has been specified.
   */
  public byte[] getUserPassword();

  /**
   * Accesses the will topic for the connection parameter set.
   *
   * @return Returns the will topic to be used when establishing the connection,
   *   or a null reference if no will topic has been specified.
   */
  public String getWillTopic();

  /***
   * Accesses the will message for the connection parameter set.
   *
   * @return Returns the will message to be used when establishing the connection,
   *   or a null reference if no will message has been specified.
   */
  public byte[] getWillMessage();

  /**
   * Accesses the will retain flag for the connection parameter set.
   *
   * @return Returns the will retain flag to be used when establishing the
   *   connection. This is only valid when a will topic has been specified.
   */
  public boolean getWillRetain();

  /**
   * Accesses the will QoS policy for the connection parameter set.
   *
   * @return Returns the will QoS policy to be used when establishing the
   *   connection. This is only valid when a will topic has been specified.
   */
  public QosPolicyType getWillQosPolicy();

  /**
   * Accesses the keep alive time for the connection parameter set.
   *
   * @return Returns the keep alive time to be used when establishing the
   *   connection. It will be an integer value between 1 and 65535 seconds. A
   *   value of zero may also be used to disable ping requests and keep alive
   *   timeouts.
   */
  public int getKeepAliveTime();

  /**
   * Accesses the transmit interval for the connection parameter set.
   *
   * @return Returns the minimum interval between transmitted transport layer
   *   packets, expressed as an integer number of milliseconds. If required,
   *   multiple MQTT packets are concatenated into a single transport layer packet
   *   in order to ensure that the transmit interval is observed.
   */
  public int getTransmitInterval();

}
