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

import java.nio.charset.CharacterCodingException;

import com.zynaptic.relay.mqttv3.ConnectionParameters;
import com.zynaptic.relay.mqttv3.QosPolicyType;

/**
 * Implements the core functionality for a single connection parameter set.
 *
 * @author Chris Holgate
 */
final class ConnectionParametersCore implements ConnectionParameters {

  // Sets the default keep alive interval to 15 minutes.
  private static final int DEFAULT_KEEP_ALIVE_TIME = 900;

  // Sets the default minimum transmit interval to 2.5 seconds.
  private static final int DEFAULT_TRANSMIT_INTERVAL = 2500;

  private final ValidatedString clientIdentifier;
  private ValidatedString userName;
  private byte[] userPassword;
  private ValidatedString willTopic;
  private byte[] willMessage;
  private boolean willRetain;
  private QosPolicyType willQosPolicy;
  private int keepAliveTime;
  private int transmitInterval;

  /**
   * Protected constructor used by the client service for creating new connection
   * parameter sets.
   *
   * @param clientIdentifier This is a string which is to be used to uniquely
   *   identify an individual MQTT client.
   */
  ConnectionParametersCore(final String clientIdentifier) {
    if (clientIdentifier == null) {
      throw new IllegalArgumentException("No valid client identifier string specified");
    }
    try {
      this.clientIdentifier = ValidatedString.create(clientIdentifier);
    } catch (final CharacterCodingException error) {
      throw new IllegalArgumentException("Client identifier string contains invalid characters", error);
    }
    userName = null;
    userPassword = null;
    willTopic = null;
    willMessage = null;
    willRetain = false;
    willQosPolicy = null;
    keepAliveTime = DEFAULT_KEEP_ALIVE_TIME;
    transmitInterval = DEFAULT_TRANSMIT_INTERVAL;
  }

  /*
   * Implements ConnectionParameters.setUserCredentials(...)
   */
  @Override
  public synchronized ConnectionParameters setUserCredentials(final String userName, final byte[] userPassword) {
    if (userName == null) {
      this.userName = null;
      this.userPassword = null;
    } else {
      try {
        this.userName = ValidatedString.create(userName);
      } catch (final CharacterCodingException error) {
        throw new IllegalArgumentException("User name string contains invalid characters", error);
      }
      if (userPassword == null) {
        this.userPassword = null;
      } else if (userPassword.length <= 0xFFFF) {
        this.userPassword = userPassword;
      } else {
        throw new IllegalArgumentException("User password exceeds maximum supported length for a byte array");
      }
    }
    return this;
  }

  /*
   * Implements ConnectionParameters.setWillParameters(...)
   */
  @Override
  public synchronized ConnectionParameters setWillParameters(final String willTopic, final byte[] willMessage,
      final boolean willRetain, final QosPolicyType willQosPolicy) {
    if ((willTopic == null) && (willMessage != null)) {
      throw new IllegalArgumentException("Will topic is required if a valid will message is specified");
    } else if ((willTopic != null) && (willMessage == null)) {
      throw new IllegalArgumentException("Will message is required if a valid will topic is specified");
    } else if (willTopic == null) {
      this.willTopic = null;
      this.willMessage = null;
      this.willRetain = false;
      this.willQosPolicy = QosPolicyType.AT_MOST_ONCE;
    } else if (willMessage.length > 0xFFFF) {
      throw new IllegalArgumentException("Will message exceeds maximum supported length for a byte array");
    } else if ((willQosPolicy == null) || (willQosPolicy == QosPolicyType.INVALID)) {
      throw new IllegalArgumentException("The specified will QoS policy is not valid");
    } else {
      try {
        this.willTopic = ValidatedString.create(willTopic);
      } catch (final CharacterCodingException error) {
        throw new IllegalArgumentException("Will topic string contains invalid characters", error);
      }
      this.willMessage = willMessage;
      this.willRetain = willRetain;
      this.willQosPolicy = willQosPolicy;
      if (!this.willTopic.isValidTopicName()) {
        throw new IllegalArgumentException("Wildcard characters '+' and '#' are not permitted in topic names");
      }
    }
    return this;
  }

  /*
   * Implements ConnectionParameters.setTimingParameters(...)
   */
  @Override
  public synchronized ConnectionParameters setTimingParameters(final int keepAliveTime, final int transmitInterval) {
    if ((keepAliveTime < 0) || (keepAliveTime > 0xFFFF)) {
      throw new IllegalArgumentException("The keep alive time must be in the valid range from 0 to 0xFFFF");
    }
    this.keepAliveTime = keepAliveTime;
    this.transmitInterval = (transmitInterval < 0) ? 0 : transmitInterval;
    return this;
  }

  /*
   * Implements ConnectionParameters.getClientIdentifier()
   */
  @Override
  public String getClientIdentifier() {
    return clientIdentifier.getString();
  }

  /*
   * Implements ConnectionParameters.getUserName()
   */
  @Override
  public synchronized String getUserName() {
    return (userName == null) ? null : userName.getString();
  }

  /*
   * Implements ConnectionParameters.getUserPassword()
   */
  @Override
  public synchronized byte[] getUserPassword() {
    return userPassword;
  }

  /*
   * Implements ConnectionParameters.getWillTopic()
   */
  @Override
  public synchronized String getWillTopic() {
    return (willTopic == null) ? null : willTopic.getString();
  }

  /*
   * Implements ConnectionParameters.getWillMessage()
   */
  @Override
  public synchronized byte[] getWillMessage() {
    return willMessage;
  }

  /*
   * Implements ConnectionParameters.getWillRetain()
   */
  @Override
  public synchronized boolean getWillRetain() {
    return willRetain;
  }

  /*
   * Implements ConnectionParameters.getWillQosPolicy()
   */
  @Override
  public synchronized QosPolicyType getWillQosPolicy() {
    return willQosPolicy;
  }

  /*
   * Implements ConnectionParameters.getKeepAliveTime()
   */
  @Override
  public synchronized int getKeepAliveTime() {
    return keepAliveTime;
  }

  /*
   * Implements ConnectionParameters.getTransmitInterval()
   */
  @Override
  public synchronized int getTransmitInterval() {
    return transmitInterval;
  }

  /**
   * Accesses the validated and formatted client identifier string.
   *
   * @return Returns the validated and formatted client identifier string.
   */
  synchronized ValidatedString getValidatedClientIdentifier() {
    return clientIdentifier;
  }

  /**
   * Accesses the validated and formatted user name string.
   *
   * @return Returns the validated and formatted user name string.
   */
  synchronized ValidatedString getValidatedUserName() {
    return userName;
  }

  /**
   * Accesses the validated and formatted will topic string.
   *
   * @return Returns the validated and formatted will topic string.
   */
  synchronized ValidatedString getValidatedWillTopic() {
    return willTopic;
  }
}
