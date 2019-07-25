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

import java.util.logging.Level;

import com.zynaptic.reaction.Deferrable;
import com.zynaptic.reaction.Deferred;
import com.zynaptic.reaction.Logger;
import com.zynaptic.reaction.Reactor;
import com.zynaptic.reaction.Signal;
import com.zynaptic.reaction.Timeable;
import com.zynaptic.reaction.sockets.SocketClosedException;

/**
 * Implements received packet dispatch for MQTT clients. This includes managing
 * publish message handshakes and server ping requests.
 *
 * @author Chris Holgate
 */
final class MqttClientPacketDispatch implements Timeable<Void>, Deferrable<ControlPacket, Void> {

  private final Reactor reactor;
  private final Logger logger;
  private final MqttClientConnectionCore clientConnection;
  private final Signal<PublishPacket> subscriberSignal;
  private int pingInterval;
  private boolean pingResponseLost;
  private boolean reconnecting;

  /**
   * Provides protected constructor for instantiating packet dispatch instances
   * from the client connection.
   *
   * @param reactor This is the reactor service which is to be used for
   *   asynchronous event management.
   * @param logger This is a logger component which may be used for logging packet
   *   dispatch activity.
   * @param clientConnection This is the client connection which instantiated the
   *   packet dispatcher.
   */
  MqttClientPacketDispatch(final Reactor reactor, final Logger logger,
      final MqttClientConnectionCore clientConnection) {
    this.reactor = reactor;
    this.logger = logger;
    this.clientConnection = clientConnection;
    subscriberSignal = reactor.newSignal();
  }

  /**
   * Starts the packet dispatch process on request from the enclosing client
   * connection.
   *
   * @param keepAliveTime This is the keep alive time to be used when generating
   *   ping requests and checking ping responses.
   */
  synchronized void start(final int keepAliveTime) {
    if (pingInterval > 0) {
      pingInterval = (keepAliveTime * 990);
      pingResponseLost = false;
      reactor.runTimerOneShot(this, 0, null);
    }
    reconnecting = false;
    clientConnection.getReceiveQueue().receivePacket().addDeferrable(this, true);
  }

  /**
   * Accesses the subscriber signal that is used to provide notifications of all
   * received publish packets.
   *
   * @return Returns the received publish packet subscriber signal.
   */
  synchronized Signal<PublishPacket> getSubscriberSignal() {
    return subscriberSignal.makeRestricted();
  }

  /*
   * Timer tick callbacks are used to drive ping packet generation at the rate
   * required by the specified keep alive time.
   */
  @Override
  public synchronized void onTick(final Void data) {

    // Check for missing ping responses during the previous ping interval. This
    // indicates a network problem and will result in the underlying connection
    // being restarted.
    if (pingResponseLost) {
      if (!reconnecting) {
        reconnecting = true;
        clientConnection.reconnect();
      }
    }

    // Issue a ping request and then wait for the required ping interval.
    else {
      pingResponseLost = true;
      reactor.runTimerOneShot(this, pingInterval, null);
      final ControlPacket pingReqPacket = new ControlPacket(ControlPacketType.PINGREQ, null);
      clientConnection.getTransmitQueue().transmitPacket(pingReqPacket);
    }
  }

  /*
   * Callback handler on completing a received packet request. This dispatches the
   * received packet to the appropriate packet processing function, based on its
   * packet type.
   */
  @Override
  public synchronized Void onCallback(final Deferred<ControlPacket> deferred, final ControlPacket controlPacket) {
    switch (controlPacket.getControlPacketType()) {

    // Process ping responses.
    case PINGRESP:
      pingResponseLost = false;
      break;

    // Process subscribe and unsubscribe acknowledgements.
    case SUBACK:
      doProcessAckResponse(controlPacket, HandshakeState.SUBSCRIBE_REQUEST);
      break;
    case UNSUBACK:
      doProcessAckResponse(controlPacket, HandshakeState.UNSUBSCRIBE_REQUEST);
      break;

    // Process publish handshake messages for locally published topics.
    case PUBACK:
      doProcessAckResponse(controlPacket, HandshakeState.QOS1_TX_PUBLISH);
      break;
    case PUBREC:
      doPublishHandshakeRelease(controlPacket);
      break;
    case PUBCOMP:
      doProcessAckResponse(controlPacket, HandshakeState.QOS2_TX_RELEASE);
      break;

    // Process publish handshake messages for subscribed topics.
    case PUBLISH:
      final PublishPacket publishPacket = (PublishPacket) controlPacket;
      switch (publishPacket.getQosPolicy()) {
      case AT_LEAST_ONCE:
        doSubscriberHandshakeQos1(publishPacket);
        break;
      case EXACTLY_ONCE:
        doSubscriberHandshakeQos2A(publishPacket);
        break;
      default:
        subscriberSignal.signal(publishPacket);
        break;
      }
      break;
    case PUBREL:
      doSubscriberHandshakeQos2B(controlPacket);
      break;

    // Handle unexpected packet types by terminating the MQTT session.
    default:
      logger.log(Level.WARNING,
          "Unexpected packet type detected (" + controlPacket.getControlPacketType() + ") - terminating MQTT session");
      clientConnection.abort();
      return null;
    }

    // Request the next packet.
    clientConnection.getReceiveQueue().receivePacket().addDeferrable(this, true);
    return null;
  }

  /*
   * Error callback handler on failing to complete a received packet request.
   * Except for clean sessions, a socket closed exception should result in a
   * transport layer reconnection attempt. Otherwise the session needs to be
   * terminated.
   */
  @Override
  public synchronized Void onErrback(final Deferred<ControlPacket> deferred, final Exception error) {

    // If the socket has been closed attempt to reconnect.
    if (error instanceof SocketClosedException) {
      if (!reconnecting) {
        reconnecting = true;
        clientConnection.reconnect();
      }
    }

    // Other error conditions result in the MQTT session being terminated.
    else {
      logger.log(Level.WARNING, "Unexpected transport layer error - terminating MQTT session", error);
      clientConnection.abort();
    }
    return null;
  }

  /*
   * Process common acknowledgement responses. The current handshake state
   * information is removed from the handshake state table.
   */
  private void doProcessAckResponse(final ControlPacket rxControlPacket, final HandshakeState expectedHandshakeState) {
    final short packetId = rxControlPacket.getPacketId();
    final HandshakeInfo handshake = clientConnection.getSendHandshakeStates().getState(packetId);

    // Pass the acknowledgement packet to the stash update callback, with subsequent
    // processing being deferred until after the updated state table has been
    // committed to the stash.
    if ((handshake != null) && (handshake.getHandshakeState() == expectedHandshakeState)) {
      clientConnection.getSendHandshakeStates().remove(packetId)
          .addDeferrable(new DeferredPacketResponse(handshake.getDeferredResponse(), rxControlPacket), true);
    }

    // Inconsistent client/server state implies a protocol violation.
    else {
      if (handshake == null) {
        logger.log(Level.WARNING, "Unknown packet identifier detected - terminating MQTT session");
      } else {
        logger.log(Level.WARNING, "Inconsistent handshake state for " + rxControlPacket.getControlPacketType() + " ("
            + handshake.getHandshakeState() + ") - terminating MQTT session");
      }
      clientConnection.abort();
    }
  }

  /*
   * Process QoS 2 handshake release packets for published messages. The current
   * handshake state information is updated in the handshake state table.
   */
  private void doPublishHandshakeRelease(final ControlPacket rxControlPacket) {
    final short packetId = rxControlPacket.getPacketId();
    final HandshakeInfo handshake = clientConnection.getSendHandshakeStates().getState(packetId);

    // Issue a publish release message in response to a publish received. Note that
    // packet transmission is deferred until after the updated handshake state has
    // been committed to the stash.
    if ((handshake != null) && (handshake.getHandshakeState() == HandshakeState.QOS2_TX_PUBLISH)) {
      final ControlPacket txControlPacket = new ControlPacket(ControlPacketType.PUBREL, packetId);
      clientConnection.getSendHandshakeStates().update(packetId, HandshakeState.QOS2_TX_RELEASE)
          .addDeferrable(new DeferredPacketSend(txControlPacket), true);
    }

    // Inconsistent client/server state implies a protocol violation.
    else {
      if (handshake == null) {
        logger.log(Level.WARNING, "Unknown packet identifier detected - terminating MQTT session");
      } else {
        logger.log(Level.WARNING, "Inconsistent handshake state for " + rxControlPacket.getControlPacketType() + " ("
            + handshake.getHandshakeState() + ") - terminating MQTT session");
      }
      clientConnection.abort();
    }
  }

  /*
   * Process QoS 1 publish packets for subscribed topics. These can be
   * acknowledged directly by a PUBACK packet without requiring an update to the
   * persisted handshake state table.
   */
  private void doSubscriberHandshakeQos1(final PublishPacket rxPublishPacket) {
    final short packetId = rxPublishPacket.getPacketId();
    final ControlPacket txControlPacket = new ControlPacket(ControlPacketType.PUBACK, packetId);
    clientConnection.getTransmitQueue().transmitPacket(txControlPacket);
    subscriberSignal.signal(rxPublishPacket);
  }

  /*
   * Process QoS 2 publish packets for subscribed topics (first phase). These are
   * acknowledged with a PUBREC packets after the transaction has been added to
   * the handshake state table.
   */
  private void doSubscriberHandshakeQos2A(final PublishPacket rxPublishPacket) {
    final short packetId = rxPublishPacket.getPacketId();
    final ControlPacket txControlPacket = new ControlPacket(ControlPacketType.PUBREC, packetId);
    final HandshakeInfo handshake = clientConnection.getRecvHandshakeStates().getState(packetId);

    // Process newly received packets. Note that packet transmission is deferred
    // until after the updated handshake state has been committed to the stash. The
    // duplicate flag is not checked since both settings are legitimate in this
    // context.
    if (handshake == null) {
      clientConnection.getRecvHandshakeStates().insert(packetId, HandshakeState.QOS2_RX_PROCESS)
          .addDeferrable(new DeferredPacketForward(rxPublishPacket, txControlPacket), true);
    }

    // Do not forward duplicate packets - just resend the response packet.
    else if ((handshake.getHandshakeState() == HandshakeState.QOS2_RX_PROCESS) && (rxPublishPacket.isDuplicate())) {
      clientConnection.getTransmitQueue().transmitPacket(txControlPacket);
    }

    // Inconsistent client/server state implies a protocol violation.
    else {
      if (rxPublishPacket.isDuplicate()) {
        logger.log(Level.WARNING, "Inconsistent handshake state for duplicate PUBLISH packet ("
            + handshake.getHandshakeState() + ") - terminating MQTT session");
      } else {
        logger.log(Level.WARNING, "Inconsistent handshake state for new PUBLISH packet ("
            + handshake.getHandshakeState() + ") - terminating MQTT session");
      }
      clientConnection.abort();
    }
  }

  /*
   * Process QoS 2 publish release packets for subscribed topics (second phase).
   * These are acknowledged with PUBCOMP packets after the transaction has been
   * removed from the handshake state table.
   */
  private void doSubscriberHandshakeQos2B(final ControlPacket rxControlPacket) {
    final short packetId = rxControlPacket.getPacketId();
    final ControlPacket txControlPacket = new ControlPacket(ControlPacketType.PUBCOMP, packetId);
    final HandshakeInfo handshake = clientConnection.getRecvHandshakeStates().getState(packetId);

    // Resend the response packet on duplicates.
    if (handshake == null) {
      clientConnection.getTransmitQueue().transmitPacket(txControlPacket);
    }

    // Defer sending the response message until the local message state has been
    // removed.
    else if (handshake.getHandshakeState() == HandshakeState.QOS2_RX_PROCESS) {
      clientConnection.getRecvHandshakeStates().remove(packetId).addDeferrable(new DeferredPacketSend(txControlPacket),
          true);
    }

    // Inconsistent client/server state implies a protocol violation.
    else {
      logger.log(Level.WARNING, "Inconsistent handshake state for PUBREL packet (" + handshake.getHandshakeState()
          + ") - terminating MQTT session");
      clientConnection.abort();
    }
  }

  /*
   * Implement deferred transmission of handshake packets after updating the
   * handshake state table.
   */
  private class DeferredPacketSend implements Deferrable<HandshakeInfo, Void> {
    private final ControlPacket controlPacket;

    private DeferredPacketSend(final ControlPacket controlPacket) {
      this.controlPacket = controlPacket;
    }

    @Override
    public Void onCallback(final Deferred<HandshakeInfo> deferred, final HandshakeInfo handshake) {
      clientConnection.getTransmitQueue().transmitPacket(controlPacket);
      return null;
    }

    @Override
    public Void onErrback(final Deferred<HandshakeInfo> deferred, final Exception error) {
      logger.log(Level.WARNING, "Fatal stash service error - terminating MQTT session", error);
      clientConnection.abort();
      return null;
    }
  }

  /*
   * Implement deferred forwarding of received publish packets after updating the
   * handshake state table.
   */
  private class DeferredPacketForward implements Deferrable<HandshakeInfo, Void> {
    private final PublishPacket rxPublishPacket;
    private final ControlPacket txControlPacket;

    private DeferredPacketForward(final PublishPacket rxPublishPacket, final ControlPacket txControlPacket) {
      this.rxPublishPacket = rxPublishPacket;
      this.txControlPacket = txControlPacket;
    }

    @Override
    public Void onCallback(final Deferred<HandshakeInfo> deferred, final HandshakeInfo handshake) throws Exception {
      clientConnection.getTransmitQueue().transmitPacket(txControlPacket);
      subscriberSignal.signal(rxPublishPacket);
      return null;
    }

    @Override
    public Void onErrback(final Deferred<HandshakeInfo> deferred, final Exception error) throws Exception {
      logger.log(Level.WARNING, "Fatal stash service error - terminating MQTT session", error);
      clientConnection.abort();
      return null;
    }
  }

  /*
   * Implement deferred processing of acknowledgement responses after updating the
   * handshake state table.
   */
  private class DeferredPacketResponse implements Deferrable<HandshakeInfo, Void> {
    private final Deferred<ControlPacket> deferredResponse;
    private final ControlPacket rxControlPacket;

    private DeferredPacketResponse(final Deferred<ControlPacket> deferredResponse,
        final ControlPacket rxControlPacket) {
      this.deferredResponse = deferredResponse;
      this.rxControlPacket = rxControlPacket;
    }

    @Override
    public Void onCallback(final Deferred<HandshakeInfo> deferred, final HandshakeInfo handshake) {
      if (deferredResponse != null) {
        deferredResponse.callback(rxControlPacket);
      }
      return null;
    }

    @Override
    public Void onErrback(final Deferred<HandshakeInfo> deferred, final Exception error) {
      if (deferredResponse != null) {
        deferredResponse.errback(error);
      }
      return null;
    }
  }
}
