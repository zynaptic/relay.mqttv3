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

import com.zynaptic.relay.mqttv3.ConnectionStatus;

/**
 * Extends the basic control packet class in order to support fields specific to
 * connection acknowledgement packets.
 *
 * @author Chris Holgate
 */
final class ConnackPacket extends ControlPacket {

  private final boolean sessionPresent;
  private final ConnectionStatus connectionStatus;

  /**
   * Provides protected constructor for connection acknowledgement packets.
   *
   * @param sessionPresent This is a boolean flag which when set to 'true'
   *   indicates that an existing session exists on the server and has been
   *   resumed.
   * @param connectionStatus This is the connection status returned by the server
   *   on a connection request.
   */
  ConnackPacket(final boolean sessionPresent, final ConnectionStatus connectionStatus) {
    super(ControlPacketType.CONNACK, null);
    this.sessionPresent = sessionPresent;
    this.connectionStatus = connectionStatus;
  }

  /**
   * Static method for creating a new connection acknowledgement packet instance
   * given a packet body for parsing. This may only be used for parsing CONNACK
   * packets.
   *
   * @param messageLength This is the remaining length field as extracted from the
   *   packet fixed header.
   * @param messageBuffer This a message buffer which is guaranteed to contain the
   *   remainder of the control packet, as determined by the messageLength
   *   parameter. The position identifier refers to the byte immediately following
   *   the fixed header.
   * @return Returns a newly constructed connection acknowledgement packet
   *   instance.
   * @throws MalformedPacketException This exception will be thrown if the packet
   *   format does not conform to the specification.
   */
  static ConnackPacket parsePacket(final int messageLength, final ByteBuffer messageBuffer)
      throws MalformedPacketException {
    final int startPosition = messageBuffer.position();

    // Check the fixed packet length.
    if (messageLength != 2) {
      messageBuffer.position(startPosition + messageLength);
      throw new MalformedPacketException("Invalid fixed packet length");
    }

    // Check the connection acknowledgement flags.
    final byte connackFlags = messageBuffer.get();
    boolean sessionPresent = false;
    if (connackFlags == 1) {
      sessionPresent = true;
    } else if (connackFlags != 0) {
      messageBuffer.position(startPosition + messageLength);
      throw new MalformedPacketException("Invalid connection acknowlegement flags");
    }

    // Check the connection status value.
    final int connackStatusEncoding = 0xFF & messageBuffer.get();
    final ConnectionStatus connackStatus = ConnectionStatus.getConnackStatus(connackStatusEncoding);
    if (connackStatus == null) {
      messageBuffer.position(startPosition + messageLength);
      throw new MalformedPacketException("Invalid connection acknowlegement status");
    }
    return new ConnackPacket(sessionPresent, connackStatus);
  }

  /**
   * Accesses the connection status value that is associated with the connection
   * acknowledgement packet.
   *
   * @return Returns the connection status value assigned by the server.
   */
  ConnectionStatus getConnectionStatus() {
    return connectionStatus;
  }

  /**
   * Accesses the session present flag that is associated with the connection
   * acknowledgement packet.
   *
   * @return Returns the session present flag assigned by the server.
   */
  boolean getSessionPresent() {
    return sessionPresent;
  }

  /*
   * Overrides ControlPacket.formatPacket()
   */
  @Override
  public ByteBuffer formatPacket() {

    // Assemble the message into a byte buffer.
    final ByteBuffer outputBuffer = ByteBuffer.allocate(4);
    outputBuffer.put((byte) getControlPacketType().getIntegerEncoding());
    outputBuffer.put((byte) 2);
    outputBuffer.put((byte) ((sessionPresent) ? 1 : 0));
    outputBuffer.put((byte) connectionStatus.getIntegerEncoding());
    return outputBuffer.flip();
  }

  /*
   * Overrides ControlPacket.tracePacket(...)
   */
  @Override
  public String tracePacket(final boolean hasTextPayload, final boolean verbose) {
    return String.format("%-11s CS: %s, SP: %b", getControlPacketType().toString(), connectionStatus, sessionPresent);
  }
}
