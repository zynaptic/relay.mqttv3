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

import java.net.InetAddress;

import javax.net.ssl.SSLContext;

import com.zynaptic.reaction.Deferred;

/**
 * Defines the MQTT client service interface which is used for establishing MQTT
 * client connections to remote MQTT servers.
 *
 * @author Chris Holgate
 */
public interface MqttClientService {

  /**
   * Specifies the default MQTT port number for unencrypted connections, as
   * registered with IANA.
   */
  public static final int MQTT_DEFAULT_PORT = 1883;

  /**
   * Specifies the default MQTT port number for connections encrypted using TLS,
   * as registered with IANA.
   */
  public static final int MQTTS_DEFAULT_PORT = 8883;

  /**
   * Creates a new connection parameters object with the specified client
   * identifier and default connection settings.
   *
   * @param clientIdentifier This is a string which is to be used to uniquely
   *   identify an individual MQTT client.
   * @return Returns a new connection parameters object initialised with the
   *   default connection settings.
   */
  public ConnectionParameters createConnectionParameters(String clientIdentifier);

  /**
   * Initiates a client connection to a remote MQTT server using a conventional
   * unencrypted TCP connection. Unencrypted connections are not recommended for
   * general use.
   *
   * @param address This is the address of the server to which the connection is
   *   being established.
   * @param port This is the TCP port on the server to which the connection is
   *   being established.
   * @param connectionParameters This is the set of connection parameters to be
   *   used when establishing the new connection.
   * @param cleanSession This is a boolean value which when set to 'true'
   *   indicates that the connection should use a clean session, with any prior
   *   connection state being discarded.
   * @return Returns a deferred event object which will have its callbacks
   *   executed on completion. On successful completion a reference to the newly
   *   created {{@link MqttClientConnection} will be passed as the callback
   *   parameter.
   */
  public Deferred<MqttClientConnection> connectClient(final InetAddress address, final int port,
      final ConnectionParameters connectionParameters, boolean cleanSession);

  /**
   * Initiates a client connection to a remote MQTT server using a TLS encrypted
   * connection over the underlying TCP link.
   *
   * @param address This is the address of the server to which the connection is
   *   being established.
   * @param port This is the TCP port on the server to which the connection is
   *   being established.
   * @param connectionParameters This is the set of connection parameters to be
   *   used when establishing the new connection.
   * @param cleanSession This is a boolean value which when set to 'true'
   *   indicates that the connection should use a clean session, with any prior
   *   connection state being discarded.
   * @param sslContext This is a standard Java SSL context object which specifies
   *   the keys and certificates to be used in establishing the TLS connection.
   * @return Returns a deferred event object which will have its callbacks
   *   executed on completion. On successful completion a reference to the newly
   *   created {{@link MqttClientConnection} will be passed as the callback
   *   parameter.
   */
  public Deferred<MqttClientConnection> connectSSLClient(final InetAddress address, final int port,
      final ConnectionParameters connectionParameters, boolean cleanSession, final SSLContext sslContext);

}
