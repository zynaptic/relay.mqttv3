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

import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;

import com.zynaptic.relay.mqttv3.ConnectionParameters;
import com.zynaptic.relay.mqttv3.QosPolicyType;

/**
 * Extends the basic control packet class in order to support fields specific to
 * connection request packets.
 *
 * @author Chris Holgate
 */
final class ConnectPacket extends ControlPacket {

  // Specify the constant protocol header field.
  private static final byte[] PROTOCOL_HEADER = new byte[] { 0x00, 0x04, 0x4D, 0x51, 0x54, 0x54 };

  // Specify the supported protocol version.
  private static final byte DEFAULT_PROTOCOL_VERSION = 0x04;

  private final byte protocolVersion;
  private final ValidatedString clientIdentifier;
  private final ValidatedString userName;
  private final byte[] password;
  private final ValidatedString willTopic;
  private final byte[] willMessage;
  private final boolean willRetain;
  private final QosPolicyType willQosPolicy;
  private final boolean cleanSession;
  private final int keepAliveTime;

  /**
   * Provides private constructor for connection request packets using parameters
   * extracted by the packet parsing method.
   *
   * @param clientIdentifier This is the client identifier string which is
   *   required by connection requests.
   * @param userName This is the user name string which is optional for connection
   *   requests. A null reference is valid if the user name is not present.
   * @param password This is the user password represented as a byte array. This
   *   is optional for connection requests and a null reference is valid if the
   *   user password is not present.
   * @param willTopic This is the topic to be used for the connection will
   *   message. This is optional for connection requests and a null reference is
   *   valid if the will topic is not present.
   * @param willMessage This is the payload to be used for the connection will
   *   message. This is optional for connection requests and a null reference is
   *   valid if the will message is not present.
   * @param willRetain This is a boolean value which indicates whether the retain
   *   flag associated with the will message is to be set.
   * @param willQosPolicy This is the QoS policy to be applied when transmitting
   *   the will message.
   * @param cleanSession This is a boolean flag which when set requests that the
   *   server establishes a new clean session, rather than reusing an existing
   *   session.
   * @param keepAliveTime This is the keep alive time which is used to detect loss
   *   of connectivity with the server.
   */
  private ConnectPacket(final byte protocolVersion, final ValidatedString clientIdentifier,
      final ValidatedString userName, final byte[] password, final ValidatedString willTopic, final byte[] willMessage,
      final boolean willRetain, final QosPolicyType willQosPolicy, final boolean cleanSession,
      final int keepAliveTime) {
    super(ControlPacketType.CONNECT, null);
    this.protocolVersion = protocolVersion;
    this.clientIdentifier = clientIdentifier;
    this.userName = userName;
    this.password = password;
    this.willTopic = willTopic;
    this.willMessage = willMessage;
    this.willRetain = willRetain;
    this.willQosPolicy = willQosPolicy;
    this.cleanSession = cleanSession;
    this.keepAliveTime = keepAliveTime;
  }

  /**
   * Provides protected constructor for connection request packets using
   * parameters encapsulated by a connection parameters object.
   *
   * @param params This is the connection parameter object which encapsulates the
   *   various connection parameter settings.
   * @param cleanSession This is a boolean flag which when set requests that the
   *   server establishes a new clean session, rather than reusing an existing
   *   session.
   */
  ConnectPacket(final ConnectionParameters params, final boolean cleanSession) {
    super(ControlPacketType.CONNECT, null);
    final ConnectionParametersCore validatedParams = (ConnectionParametersCore) params;
    protocolVersion = DEFAULT_PROTOCOL_VERSION;
    clientIdentifier = validatedParams.getValidatedClientIdentifier();
    userName = validatedParams.getValidatedUserName();
    password = validatedParams.getUserPassword();
    willTopic = validatedParams.getValidatedWillTopic();
    willMessage = validatedParams.getWillMessage();
    willRetain = validatedParams.getWillRetain();
    willQosPolicy = validatedParams.getWillQosPolicy();
    this.cleanSession = cleanSession;
    keepAliveTime = validatedParams.getKeepAliveTime();
  }

  /**
   * Static method for creating a new connect packet instance given a packet body
   * for parsing. This may only be used for parsing CONNECT packets.
   *
   * @param messageLength This is the remaining length field as extracted from the
   *   packet fixed header.
   * @param messageBuffer This a message buffer which is guaranteed to contain the
   *   remainder of the control packet, as determined by the messageLength
   *   parameter. The position identifier refers to the byte immediately following
   *   the fixed header.
   * @return Returns a newly constructed connect packet instance.
   * @throws MalformedPacketException This exception will be thrown if the packet
   *   format does not conform to the specification.
   */
  static ConnectPacket parsePacket(final int messageLength, final ByteBuffer messageBuffer)
      throws MalformedPacketException {
    final int startPosition = messageBuffer.position();
    try {

      // Check that the first 7 bytes match the required fixed protocol header.
      for (int i = 0; i < PROTOCOL_HEADER.length; i++) {
        if (messageBuffer.get() != PROTOCOL_HEADER[i]) {
          messageBuffer.position(startPosition + messageLength);
          throw new MalformedPacketException("Unknown or invalid protocol");
        }
      }

      // Extract the connection flag bits.
      final byte requestedProtocolVersion = messageBuffer.get();
      final byte connectFlags = messageBuffer.get();
      final boolean userNameFlag = (connectFlags & 0x80) != 0;
      final boolean passwordFlag = (connectFlags & 0x40) != 0;
      final boolean willRetainFlag = (connectFlags & 0x20) != 0;
      final QosPolicyType willQosValue = QosPolicyType.getQosPolicyType((connectFlags & 0x18) >> 3);
      final boolean willFlag = (connectFlags & 0x04) != 0;
      final boolean cleanSessionFlag = (connectFlags & 0x02) != 0;

      // Perform validity checking on the connection flag settings.
      if ((connectFlags & 0x01) != 0) {
        messageBuffer.position(startPosition + messageLength);
        throw new MalformedPacketException("Invalid reserved value in connection flags");
      }
      if (willQosValue == null) {
        messageBuffer.position(startPosition + messageLength);
        throw new MalformedPacketException("Invalid will QoS value in connection flags");
      }
      if (!willFlag) {
        if ((willQosValue.getIntegerEncoding() != 0) || willRetainFlag) {
          messageBuffer.position(startPosition + messageLength);
          throw new MalformedPacketException("Invalid will QoS value or retain setting in connection flags");
        }
      }
      if (passwordFlag & !userNameFlag) {
        messageBuffer.position(startPosition + messageLength);
        throw new MalformedPacketException("Password selected without required user name in connection flags");
      }

      // Extract the keep alive time, client identifier and the optional will and
      // credential fields.
      final int keepAliveValue = 0xFFFF & messageBuffer.getShort();
      final ValidatedString clientIdentifierValue = ValidatedString.create(messageBuffer);
      final ValidatedString willTopicValue = (willFlag) ? ValidatedString.create(messageBuffer) : null;
      byte[] willMessageValue = null;
      if (willFlag) {
        final int willMessageLength = 0xFFFF & messageBuffer.getShort();
        willMessageValue = new byte[willMessageLength];
        messageBuffer.get(willMessageValue);
      }
      final ValidatedString userNameValue = (userNameFlag) ? ValidatedString.create(messageBuffer) : null;
      byte[] passwordValue = null;
      if (passwordFlag) {
        final int passwordLength = 0xFFFF & messageBuffer.getShort();
        passwordValue = new byte[passwordLength];
        messageBuffer.get(passwordValue);
      }

      // Check that the extracted payload length matches the declared packet length.
      if (messageBuffer.position() != startPosition + messageLength) {
        messageBuffer.position(startPosition + messageLength);
        throw new MalformedPacketException("Payload data format does not match declared packet length");
      }

      // Create the new connection packet.
      return new ConnectPacket(requestedProtocolVersion, clientIdentifierValue, userNameValue, passwordValue,
          willTopicValue, willMessageValue, willRetainFlag, willQosValue, cleanSessionFlag, keepAliveValue);
    }

    // Check for invalid string encodings.
    catch (final CharacterCodingException error) {
      throw new MalformedPacketException("Invalid UTF-8 encoded string in connect packet", error);
    }

    // Check that the extracted payload length does not exceed the declared packet
    // length.
    catch (final BufferUnderflowException error) {
      messageBuffer.position(startPosition + messageLength);
      throw new MalformedPacketException("Payload data format does not match declared packet length", error);
    }
  }

  /*
   * Overrides ControlPacket.formatPacket()
   */
  @Override
  ByteBuffer formatPacket() {
    byte[] willTopicBytes = null;
    byte[] userNameBytes = null;

    // Create required payload elements and calculate the initial message length.
    final byte[] clientIdBytes = clientIdentifier.getByteEncoding();
    int messageLength = 10 + clientIdBytes.length;

    // Set up the connection flags based on the packet configuration.
    byte connectFlags = 0x00;
    if (cleanSession) {
      connectFlags |= 0x02;
    }

    // Set up the will flags and corresponding payload entries.
    if (willTopic != null) {
      connectFlags |= 0x04;
      connectFlags |= (willQosPolicy == null) ? 0 : willQosPolicy.getIntegerEncoding() << 3;
      if (willRetain) {
        connectFlags |= 0x20;
      }
      willTopicBytes = willTopic.getByteEncoding();
      messageLength += willTopicBytes.length + willMessage.length + 2;
    }

    // Set up the user credential flags and corresponding payload entries.
    if (userName != null) {
      connectFlags |= 0x80;
      userNameBytes = userName.getByteEncoding();
      messageLength += userNameBytes.length;
    }
    if (password != null) {
      connectFlags |= 0x40;
      messageLength += password.length + 2;
    }

    // Assemble the message into a byte buffer.
    final ByteBuffer outputBuffer = ByteBuffer.allocate(messageLength + 5);
    outputBuffer.put((byte) getControlPacketType().getIntegerEncoding());
    outputBuffer.put(VarLenQuantity.create(messageLength).getByteEncoding());
    outputBuffer.put(PROTOCOL_HEADER);
    outputBuffer.put(protocolVersion);
    outputBuffer.put(connectFlags);
    outputBuffer.putShort((short) keepAliveTime);
    outputBuffer.put(clientIdBytes);
    if (willTopicBytes != null) {
      outputBuffer.put(willTopicBytes);
      outputBuffer.putShort((short) willMessage.length);
      outputBuffer.put(willMessage);
    }
    if (userNameBytes != null) {
      outputBuffer.put(userNameBytes);
    }
    if (password != null) {
      outputBuffer.putShort((short) password.length);
      outputBuffer.put(password);
    }
    return outputBuffer.flip();
  }

  /*
   * Overrides ControlPacket.tracePacket(...)
   */
  @Override
  String tracePacket(final boolean hasTextPayload, final boolean verbose) {
    final StringBuilder stringBuilder = new StringBuilder(
        String.format("%-11s UN: %b, PW: %b, WR: %b, WQ: %s, WF: %b, CS: %b", getControlPacketType().toString(),
            userName != null, password != null, willRetain,
            (willQosPolicy == null) ? "UNUNSED" : willQosPolicy.toString(), willTopic != null, cleanSession));
    if (verbose) {
      stringBuilder.append("\n    Client ID    : ").append(clientIdentifier.getString());
      if (userName != null) {
        stringBuilder.append("\n    User name    : ").append(userName.getString());
      }
      if (hasTextPayload && (password != null)) {
        stringBuilder.append("\n    Password     : ").append(new String(password, StandardCharsets.UTF_8));
      }
      if (willTopic != null) {
        stringBuilder.append("\n    Will topic   : ").append(willTopic.getString());
      }
      if (hasTextPayload && (willMessage != null)) {
        stringBuilder.append("\n    Will message : ").append(new String(willMessage, StandardCharsets.UTF_8));
      }
    }
    return stringBuilder.toString();
  }
}
