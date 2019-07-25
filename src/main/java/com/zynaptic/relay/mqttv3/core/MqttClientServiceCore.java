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

import java.net.InetAddress;

import javax.net.ssl.SSLContext;

import com.zynaptic.reaction.Deferred;
import com.zynaptic.reaction.Logger;
import com.zynaptic.reaction.Reactor;
import com.zynaptic.reaction.sockets.SocketService;
import com.zynaptic.relay.mqttv3.ConnectionParameters;
import com.zynaptic.relay.mqttv3.MqttClientConnection;
import com.zynaptic.relay.mqttv3.MqttClientService;
import com.zynaptic.stash.StashService;

/**
 * Implements the core functionality of the MQTT client service.
 *
 * @author Chris Holgate
 */
public class MqttClientServiceCore implements MqttClientService {

  // Specify the identifier to be used for generated log messages.
  private static final String LOGGER_ID = "com.zynaptic.relay.mqttv3";

  // Specify the maximum number of in-flight publish packets that are supported.
  private static final int MAX_ACTIVE_PACKETS = 256;

  private final Reactor reactor;
  private final Logger logger;
  private final StashService stashService;
  private final SocketService socketService;

  /**
   * Provides the public constructor for the MQTT client service.
   *
   * @param reactor This is the reactor service which is to be used for
   *   asynchronous event management.
   * @param stashService This is the stash service which is to be used for storing
   *   persistent state information.
   * @param socketService This is the socket service which is to be used for
   *   establishing TCL and TLS network connections.
   */
  public MqttClientServiceCore(final Reactor reactor, final StashService stashService,
      final SocketService socketService) {
    this.reactor = reactor;
    logger = reactor.getLogger(LOGGER_ID);
    this.stashService = stashService;
    this.socketService = socketService;
  }

  /*
   * Implements MqttClientService.createConnectionParameters(...)
   */
  @Override
  public ConnectionParameters createConnectionParameters(final String clientIdentifier) {
    return new ConnectionParametersCore(clientIdentifier);
  }

  /*
   * Implements MqttClientService.connectClient(...)
   */
  @Override
  public Deferred<MqttClientConnection> connectClient(final InetAddress address, final int port,
      final ConnectionParameters connectionParameters, final boolean cleanSession) {
    final MqttClientConnectionCore clientConnection = new MqttClientConnectionCore(reactor, logger, stashService,
        socketService, address, port, cleanSession, null, MAX_ACTIVE_PACKETS);
    return clientConnection.setup(connectionParameters);
  }

  /*
   * Implements MqttClientService.connectSSLClient(...)
   */
  @Override
  public Deferred<MqttClientConnection> connectSSLClient(final InetAddress address, final int port,
      final ConnectionParameters connectionParameters, final boolean cleanSession, final SSLContext sslContext) {
    final MqttClientConnectionCore clientConnection = new MqttClientConnectionCore(reactor, logger, stashService,
        socketService, address, port, cleanSession, sslContext, MAX_ACTIVE_PACKETS);
    return clientConnection.setup(connectionParameters);
  }
}
