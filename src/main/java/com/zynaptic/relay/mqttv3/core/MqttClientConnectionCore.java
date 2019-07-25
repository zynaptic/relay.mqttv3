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
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.TreeMap;
import java.util.logging.Level;

import javax.net.ssl.SSLContext;

import com.zynaptic.reaction.Deferred;
import com.zynaptic.reaction.DeferredConcentrator;
import com.zynaptic.reaction.Logger;
import com.zynaptic.reaction.Reactor;
import com.zynaptic.reaction.sockets.SocketHandle;
import com.zynaptic.reaction.sockets.SocketService;
import com.zynaptic.relay.mqttv3.ConnectionParameters;
import com.zynaptic.relay.mqttv3.ConnectionStatus;
import com.zynaptic.relay.mqttv3.MqttClientConnection;
import com.zynaptic.relay.mqttv3.MqttClientPublisher;
import com.zynaptic.relay.mqttv3.MqttClientSubscriber;
import com.zynaptic.relay.mqttv3.QosPolicyType;
import com.zynaptic.relay.mqttv3.TopicFilter;
import com.zynaptic.stash.StashService;

/**
 * Implements the core functionality for a single MQTT client connection.
 *
 * @author Chris Holgate
 */
class MqttClientConnectionCore implements MqttClientConnection {

  private final Reactor reactor;
  private final Logger logger;
  private final SocketService socketService;
  private final InetAddress address;
  private final int port;
  private final boolean cleanSession;
  private final SSLContext sslContext;
  private ConnectionParameters connectionParameters;
  private Deferred<MqttClientConnection> deferredSetup;
  private final HandshakeTable sendHandshakeStates;
  private final HandshakeTable recvHandshakeStates;
  private final MqttClientPacketDispatch clientPacketDispatch;
  private ConnectionStatus connectionStatus;
  private TransmitQueue transmitQueue;
  private ReceiveQueue receiveQueue;
  private Iterator<HandshakeInfo> retransmitIterator;

  /*
   * Protected constructor used by the client service for creating new client
   * connections.
   */
  MqttClientConnectionCore(final Reactor reactor, final Logger logger, final StashService stashService,
      final SocketService socketService, final InetAddress address, final int port, final boolean cleanSession,
      final SSLContext sslContext, final int maxActivePackets) {
    this.reactor = reactor;
    this.logger = logger;
    this.socketService = socketService;
    this.address = address;
    this.port = port;
    this.cleanSession = cleanSession;
    this.sslContext = sslContext;
    sendHandshakeStates = new HandshakeTable(reactor, logger, stashService, maxActivePackets);
    recvHandshakeStates = new HandshakeTable(reactor, logger, stashService, 0);
    clientPacketDispatch = new MqttClientPacketDispatch(reactor, logger, this);
  }

  /*
   * Protected method used by the client service for initiating client connection
   * setup.
   */
  synchronized Deferred<MqttClientConnection> setup(final ConnectionParameters connectionParameters) {
    this.connectionParameters = connectionParameters;

    // Set up the stash service identifiers for client state persistence.
    final String baseStashId = "com.zynaptic.relay.mqtt3." + address.getHostAddress().replace('.', '-') + "-" + port;
    final String pubStatesStashId = baseStashId + ".pub";
    final String pubStatesStashName = "MQTTv3 client publish state for " + address.getHostAddress() + ":" + port;
    final String subStatesStashId = baseStashId + ".sub";
    final String subStatesStashName = "MQTTv3 client subscribe state for " + address.getHostAddress() + ":" + port;

    // Initialise the transmit and receive handshake tables.
    final DeferredConcentrator<Boolean> deferredConcentrator = reactor.newDeferredConcentrator();
    deferredConcentrator.addInputDeferred(sendHandshakeStates.init(pubStatesStashId, pubStatesStashName));
    deferredConcentrator.addInputDeferred(recvHandshakeStates.init(subStatesStashId, subStatesStashName));
    deferredConcentrator.getOutputDeferred().addCallback(x -> connectionStateSetupHandler())
        .addErrback(x -> commonSetupErrorHandler(x)).terminate();

    // Defer setup completion until after the connection handshake.
    deferredSetup = reactor.newDeferred();
    return deferredSetup.makeRestricted();
  }

  /*
   * Implements MqttClientConnection.getConnectionStatus()
   */
  @Override
  public synchronized ConnectionStatus getConnectionStatus() {
    return connectionStatus;
  }

  /*
   * Implements MqttClientConnection.createPublisher(...)
   */
  @Override
  public MqttClientPublisher createPublisher(final String topicName, final QosPolicyType qosPolicy) {
    return new MqttClientPublisherCore(reactor, this, topicName, qosPolicy);
  }

  /*
   * Implements MqttClientConnection.createTopicFilter(...)
   */
  @Override
  public TopicFilter createTopicFilter(final String filterPattern, final QosPolicyType qosPolicy) {
    return new TopicFilterCore(filterPattern, qosPolicy);
  }

  /*
   * Implements MqttClientConnection.createSubscriber(...)
   */
  @Override
  public MqttClientSubscriber createSubscriber(final TopicFilter topicFilter) {
    final ArrayList<TopicFilter> topicFilterList = new ArrayList<TopicFilter>(1);
    topicFilterList.add(topicFilter);
    return createSubscriber(topicFilterList);
  }

  /*
   * Implements MqttClientConnection.createSubscriber(...)
   */
  @Override
  public MqttClientSubscriber createSubscriber(final List<TopicFilter> topicFilterList) {
    final MqttClientSubscriberCore subscribedTopic = new MqttClientSubscriberCore(reactor, this, topicFilterList);
    clientPacketDispatch.getSubscriberSignal().subscribe(subscribedTopic);
    return subscribedTopic;
  }

  /*
   * Implements MqttClientConnection.startPacketDispatch()
   */
  @Override
  public synchronized void startPacketDispatch() {
    clientPacketDispatch.start(connectionParameters.getKeepAliveTime());
  }

  void reconnect() {
    // TODO: Need to clear out deferred callbacks in the send handshake table.
  }

  void abort() {
    // TODO: Need to handle connection aborts.
  }

  /*
   * Protected method for accessing the connection transmit queue.
   */
  TransmitQueue getTransmitQueue() {
    return transmitQueue;
  }

  /*
   * Protected method for accessing the connection receive queue.
   */
  ReceiveQueue getReceiveQueue() {
    return receiveQueue;
  }

  /*
   * Protected method for accessing the message send handshake state table.
   */
  HandshakeTable getSendHandshakeStates() {
    return sendHandshakeStates;
  }

  /*
   * Protected method for accessing the message receive handshake state table.
   */
  HandshakeTable getRecvHandshakeStates() {
    return recvHandshakeStates;
  }

  /*
   * Protected method for removing a subscribed topic. TODO: Should this be in the
   * public API?
   */
  void removeSubscribedTopic(final MqttClientSubscriberCore subscribedTopic) {
    clientPacketDispatch.getSubscriberSignal().unsubscribe(subscribedTopic);
  }

  /*
   * Callback on completing connection state recovery. Initiates a transport
   * socket open request.
   */
  private synchronized Void connectionStateSetupHandler() {
    Deferred<SocketHandle> deferredOpen;
    if (sslContext == null) {
      deferredOpen = socketService.openClient(address, port);
    } else {
      deferredOpen = socketService.openSSLClient(address, port, sslContext);
    }
    deferredOpen.addCallback(x -> socketOpenedCallbackHandler(x)).addErrback(x -> commonSetupErrorHandler(x))
        .terminate();
    return null;
  }

  /*
   * Callback on opening a transport socket. Initiate the MQTT connection request.
   */
  private synchronized Void socketOpenedCallbackHandler(final SocketHandle openedSocketHandle) {
    transmitQueue = new TransmitQueue(reactor, logger, socketService, openedSocketHandle,
        connectionParameters.getTransmitInterval());
    receiveQueue = new ReceiveQueue(reactor, logger, socketService, openedSocketHandle);
    final ControlPacket txPacket = new ConnectPacket(connectionParameters, cleanSession);
    transmitQueue.transmitPacket(txPacket);
    receiveQueue.receivePacket().addCallback(x -> connackPacketReceiveHandler(x))
        .addErrback(x -> commonSetupErrorHandler(x)).terminate();
    return null;
  }

  /*
   * Callback on having sent an MQTT connect packet and received the
   * acknowledgement. Check the validity of the connection acknowledgement packet
   * and then transmit any unacknowledged packets.
   */
  private synchronized Void connackPacketReceiveHandler(final ControlPacket controlPacket) {
    boolean sessionPresent = false;

    // An unrecognised packet is treated as an unsupported protocol.
    if (controlPacket.getControlPacketType() == ControlPacketType.CONNACK) {
      final ConnackPacket connackPacket = (ConnackPacket) controlPacket;
      connectionStatus = connackPacket.getConnectionStatus();
      sessionPresent = connackPacket.getSessionPresent();
    } else {
      connectionStatus = ConnectionStatus.UNSUPPORTED_PROTOCOL;
    }

    // Return failed connection status values to the original caller.
    if (connectionStatus != ConnectionStatus.ACCEPTED) {
      if (deferredSetup != null) {
        deferredSetup.callback(this);
        deferredSetup = null;
      }
      return null;
    }

    // If a session is being recovered, push all unacknowledged publish packets into
    // the transmit queue. The transmission order is inferred from the sequence IDs,
    // which are normalised when loading the handshake state map so that they are
    // guaranteed not to wrap.
    if (sessionPresent) {
      logger.log(Level.INFO, "Session present - retransmitting outstanding packets");
      final TreeMap<Short, HandshakeInfo> sortedHandshakeInfos = new TreeMap<Short, HandshakeInfo>();
      for (final HandshakeInfo tableEntry : sendHandshakeStates.getEntries()) {
        sortedHandshakeInfos.put(tableEntry.getSequenceId(), tableEntry);
      }
      retransmitIterator = sortedHandshakeInfos.values().iterator();
      retransmitNextPacket();
    }

    // If a session is not being recovered by the server, discard any local state.
    else {
      logger.log(Level.INFO, "Session not present - discarding outstanding packets");
      final DeferredConcentrator<Boolean> deferredConcentrator = reactor.newDeferredConcentrator();
      deferredConcentrator.addInputDeferred(sendHandshakeStates.clear());
      deferredConcentrator.addInputDeferred(recvHandshakeStates.clear());
      deferredConcentrator.getOutputDeferred().addCallback(x -> sessionClearedCallbackHandler())
          .addErrback(x -> commonSetupErrorHandler(x)).terminate();
    }
    return null;
  }

  /*
   * Attempts to retransmit the next packet, as identified by the retransmit
   * iterator. Should be called with the synchronization lock held.
   */
  private void retransmitNextPacket() {
    while (retransmitIterator.hasNext()) {
      final HandshakeInfo retransmitHandshake = retransmitIterator.next();
      switch (retransmitHandshake.getHandshakeState()) {

      // Load the publish packet data for retransmission if required.
      // TODO: Consider including subscribe and unsubscribe requests here do deal with
      // lost acks on a reconnect condition.
      case QOS1_TX_PUBLISH:
      case QOS2_TX_PUBLISH:
        sendHandshakeStates.getPacketData(retransmitHandshake.getPacketId()).addCallback(x -> retransmitRawPacket(x))
            .addErrback(x -> commonSetupErrorHandler(x)).terminate();
        return;

      // Retransmit the release packet if required.
      case QOS2_TX_RELEASE:
        final ControlPacket releasePacket = new ControlPacket(ControlPacketType.PUBREL,
            retransmitHandshake.getPacketId());
        transmitQueue.transmitPacket(releasePacket);
        break;
      default:
        // Continue
      }
    }
    retransmitIterator = null;
    if (deferredSetup != null) {
      deferredSetup.callback(this);
      deferredSetup = null;
    }
  }

  /*
   * Adds a newly loaded raw packet to the transmit queue and then requests the
   * next retransmit packet.
   */
  private synchronized Void retransmitRawPacket(final byte[] packetData) {
    transmitQueue.transmitRawPacket(ByteBuffer.wrap(packetData));
    retransmitNextPacket();
    return null;
  }

  /*
   * Callback on clearing the client session state. This completes the setup
   * process.
   */
  private synchronized Void sessionClearedCallbackHandler() {
    if (deferredSetup != null) {
      deferredSetup.callback(this);
      deferredSetup = null;
    }
    return null;
  }

  /*
   * Common error handling for any exceptions generated during the connection
   * setup phase.
   */
  private synchronized Void commonSetupErrorHandler(final Exception error) {
    if (deferredSetup != null) {
      deferredSetup.errback(error);
      deferredSetup = null;
    }
    // TODO: Handle exceptions on a socket re-open attempt.
    return null;
  }
}
