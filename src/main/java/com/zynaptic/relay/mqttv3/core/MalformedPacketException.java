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

/**
 * Exception class used to indicate that an MQTT packet failed to parse because
 * it did not conform to the MQTT specification.
 *
 * @author Chris Holgate
 */
class MalformedPacketException extends Exception {
  private static final long serialVersionUID = -6277726084230085871L;

  /**
   * Provides standard constructor for a given error message.
   *
   * @param msg This is the error message associated with the exception condition.
   */
  public MalformedPacketException(final String msg) {
    super(msg);
  }

  /**
   * Provides standard constructor for a given error message and cause.
   *
   * @param msg This is the error message associated with the exception condition.
   * @param cause This is the cause of the exception condition.
   */
  public MalformedPacketException(final String msg, final Exception cause) {
    super(msg, cause);
  }
}
