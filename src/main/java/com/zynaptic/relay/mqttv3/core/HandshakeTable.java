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

import java.util.Collection;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.logging.Level;

import com.zynaptic.reaction.Deferrable;
import com.zynaptic.reaction.Deferred;
import com.zynaptic.reaction.Logger;
import com.zynaptic.reaction.Reactor;
import com.zynaptic.stash.InvalidStashIdException;
import com.zynaptic.stash.StashObjectEntry;
import com.zynaptic.stash.StashService;

/**
 * Implements a persisted handshake state table that may be used to track the
 * current transfer state of publish and subscribe messages.
 *
 * @author Chris Holgate
 */
final class HandshakeTable {

  private final Reactor reactor;
  private final Logger logger;
  private final StashService stashService;
  private final int maxActivePackets;
  private final Random random;
  private final Set<Short> activePacketIds;
  private StashObjectEntry<short[]> stashEntry;
  private Map<Short, HandshakeInfo> stateTable;
  private String baseStashId;
  private String baseStashName;
  private short nextSequenceId;

  /**
   * Provides the protected constructor for a handshake state table.
   *
   * @param reactor This is the reactor service which is to be used for
   *   asynchronous event management.
   * @param logger This is a logger component which may be used for logging
   *   handshake table activity.
   * @param stashService This is the stash service which is to be used for storing
   *   persistent state information.
   * @param maxActivePackets This is the maximum number of active packets
   *   supported by the handshake state table. It should be at least 1 and not
   *   exceed 0x7FFF. Setting this value to zero disables the local generation of
   *   packet identifiers.
   */
  HandshakeTable(final Reactor reactor, final Logger logger, final StashService stashService,
      final int maxActivePackets) {
    this.reactor = reactor;
    this.logger = logger;
    this.stashService = stashService;
    this.maxActivePackets = maxActivePackets;
    if (maxActivePackets > 0x7FFF) {
      throw new IllegalArgumentException("Requested invalid maximum number of in-flight packets");
    } else if (maxActivePackets <= 0) {
      random = null;
      activePacketIds = null;
    } else {
      random = new Random();
      activePacketIds = new TreeSet<Short>();
    }
    nextSequenceId = 0;
  }

  /**
   * Initialises the handshake state table using a given stash identifier and
   * entry name.
   *
   * @param stashId This is the stash entry identifier which is to be used as the
   *   identifier for the handshake state table and also as the base identifier
   *   for the packet data stash entries.
   * @param stashName This is the entry name which is to be used as the name for
   *   the handshake state table and also as the base name for the packet data
   *   stash entries.
   * @return Returns a deferred event object which will have its callbacks
   *   executed on completion. A boolean value will be passed as the callback
   *   parameter which will normally be set to 'true'.
   */
  synchronized Deferred<Boolean> init(final String stashId, final String stashName) {
    baseStashId = stashId + ".packets.";
    baseStashName = stashName + " - packet ";

    // Recover existing stash entry contents.
    if (stashService.hasStashId(stashId)) {
      logger.log(Level.FINE, "Recovering stash entry " + stashId);
      try {
        stashEntry = stashService.getStashObjectEntry(stashId, short[].class);
        return stashEntry.getCurrentValue().addCallback(x -> completeTableLoading(x));
      } catch (final Exception error) {
        return reactor.failDeferred(error);
      }
    }

    // Register the stash entry if it is not already present.
    else {
      logger.log(Level.FINE, "Initialising stash entry " + stashId);
      return stashService.registerStashObjectId(stashId, stashName, new short[0])
          .addCallback(x -> completeTableRegistration(x));
    }
  }

  /**
   * Clears the contents of the handshake state table. Packet data is not cleared,
   * since any residual packet data is invalidated by the removal of the
   * associated state table entry. Note that this means invalidated packet data
   * may be retained in persistent storage.
   *
   * @return Returns a deferred event object which will have its callbacks
   *   executed on successful completion. A boolean value will be passed as the
   *   callback parameter which will normally be set to 'true'.
   */
  synchronized Deferred<Boolean> clear() {
    if (activePacketIds != null) {
      activePacketIds.clear();
    }
    stateTable.clear();
    return stashEntry.setCurrentValue(serializeTable());
  }

  /**
   * Gets the next available packet identifier for the handshake state table. This
   * will then be reserved as a unique identifier until it is released by calling
   * the 'remove' method. An upper limit of 'maxActivePackets' packet identifiers
   * is imposed here.
   *
   * @return Returns a unique active packet identifier for this handshake state
   *   table, or a null reference if there are no packet identifiers available.
   */
  synchronized Short getAvailablePacketId() {
    if ((activePacketIds == null) || (activePacketIds.size() > maxActivePackets)) {
      return null;
    }
    short packetId;
    do {
      packetId = (short) random.nextInt();
    } while (activePacketIds.contains(packetId));
    activePacketIds.add(packetId);
    return packetId;
  }

  /**
   * Accesses the current handshake state information for a given packet
   * identifier.
   *
   * @param packetId This is the packet identifier for which the current handshake
   *   state is being checked.
   * @return Returns the current handshake state associated with the packet
   *   identifier, or a null reference if no handshake for that packet identifier
   *   is currently active.
   */
  synchronized HandshakeInfo getState(final short packetId) {
    return stateTable.get(packetId);
  }

  /**
   * Accesses the packet data associated with a given packet identifier. This will
   * be the original publish packet, serialized as a byte array.
   *
   * @param packetId This is the packet identifier for which the current publish
   *   packet data is being accessed.
   * @return Returns a byte buffer containing the serialized publish packet data
   *   associated with the packet identifier, or a null reference if no handshake
   *   for that packet identifier is currently active.
   */
  Deferred<byte[]> getPacketData(final short packetId) {
    final String stashId = baseStashId + packetId;
    if (!stashService.hasStashId(stashId)) {
      return reactor.failDeferred(new InvalidStashIdException("Invalid packet identifier detected"));
    }
    try {
      return stashService.getStashObjectEntry(stashId, byte[].class).getCurrentValue();
    } catch (final Exception error) {
      return reactor.failDeferred(error);
    }
  }

  /**
   * Accesses the full set of active handshake transactions as a set of handshake
   * information objects.
   *
   * @return Returns the full set of active handshake transactions held by the
   *   handshake state table.
   */
  synchronized Collection<HandshakeInfo> getEntries() {
    return stateTable.values();
  }

  /**
   * Inserts a new handshake transaction without associated packet data into the
   * packet handshake state table.
   *
   * @param packetId This is the packet identifier which is to be used for the
   *   inserted transaction.
   * @param handshakeState This is the initial handshake state which is to be
   *   associated with the inserted transaction.
   * @return Returns a deferred event object which will have its callbacks
   *   executed on successful completion. A reference to the newly created
   *   handshake state information object will be passed as the callback
   *   parameter.
   */
  synchronized Deferred<HandshakeInfo> insert(final short packetId, final HandshakeState handshakeState) {
    final HandshakeInfo handshake = new HandshakeInfo(handshakeState, packetId, nextSequenceId++);
    stateTable.put(packetId, handshake);
    return stashEntry.setCurrentValue(serializeTable()).addCallback(x -> handshake);
  }

  /**
   * Inserts a new handshake transaction with associated packet data into the
   * packet handshake state table.
   *
   * @param packetId This is the packet identifier which is to be used for the
   *   inserted transaction.
   * @param packetData This is the serialized publish packet data which is to be
   *   transferred during the transaction.
   * @param handshakeState This is the initial handshake state which is to be
   *   associated with the inserted transaction.
   * @return Returns a deferred event object which will have its callbacks
   *   executed on successful completion. A reference to the newly created
   *   handshake state information object will be passed as the callback
   *   parameter.
   */
  synchronized Deferred<HandshakeInfo> insert(final short packetId, final byte[] packetData,
      final HandshakeState handshakeState) {
    if (stateTable.containsKey(packetId)) {
      return reactor.failDeferred(new IllegalStateException("Duplicate packet identifier detected"));
    }
    final String dataStashId = baseStashId + packetId;
    final String dataStashName = baseStashName + packetId;
    final Deferred<HandshakeInfo> deferredInsert = reactor.newDeferred();

    // Overwrite stale packet data if any is present.
    if (stashService.hasStashId(dataStashId)) {
      try {
        stashService.getStashObjectEntry(dataStashId, byte[].class).setCurrentValue(packetData)
            .addDeferrable(new PacketDataWriteCompleteHandler(packetId, handshakeState, deferredInsert), true);
      } catch (final Exception error) {
        deferredInsert.errback(error);
      }
    }

    // Store the packet data as a new stash data item.
    else {
      stashService.registerStashObjectId(dataStashId, dataStashName, packetData).addCallback(x -> true)
          .addDeferrable(new PacketDataWriteCompleteHandler(packetId, handshakeState, deferredInsert), true);
    }
    return deferredInsert.makeRestricted();
  }

  /**
   * Updates the handshake state associated with a given packet identifier.
   *
   * @param packetId This is the packet identifier for which the associated
   *   handshake state is being updated.
   * @param handshakeState This is the updated handshake state which is being
   *   associated with the packet identifier.
   * @return Returns a deferred event object which will have its callbacks
   *   executed on successful completion. A reference to the associated handshake
   *   state information object will be passed as the callback parameter.
   */
  synchronized Deferred<HandshakeInfo> update(final short packetId, final HandshakeState handshakeState) {
    if (!stateTable.containsKey(packetId)) {
      return reactor.failDeferred(new IllegalStateException("Missing packet identifier detected"));
    }
    final HandshakeInfo handshake = stateTable.get(packetId);
    handshake.setHandshakeState(handshakeState, nextSequenceId++);
    return stashEntry.setCurrentValue(serializeTable()).addCallback(x -> handshake);
  }

  /**
   * Removes the handshake state information and stored packet data associated
   * with a given packet identifier. Also removes the packet identifier from the
   * active set.
   *
   * @param packetId This is the packet identifier for which the associated
   *   handshake state information and packet data is being removed.
   * @return Returns a deferred event object which will have its callbacks
   *   executed on successful completion. A reference to the removed handshake
   *   state information object will be passed as the callback parameter.
   */
  synchronized Deferred<HandshakeInfo> remove(final short packetId) {
    final String dataStashId = baseStashId + packetId;
    stashService.invalidateStashId(dataStashId).discard();
    final HandshakeInfo handshake = stateTable.get(packetId);
    stateTable.remove(packetId);
    if (activePacketIds != null) {
      activePacketIds.remove(packetId);
    }
    return stashEntry.setCurrentValue(serializeTable()).addCallback(x -> handshake);
  }

  /*
   * Convert the handshake state table to a serialized form for storage in the
   * stash. Should only be called with the synchronization lock held.
   */
  private short[] serializeTable() {
    final short[] serialized = new short[3 * stateTable.size()];
    int i = 0;
    for (final Map.Entry<Short, HandshakeInfo> tableEntry : stateTable.entrySet()) {
      serialized[i++] = tableEntry.getKey();
      serialized[i++] = tableEntry.getValue().getSequenceId();
      serialized[i++] = tableEntry.getValue().getHandshakeState().getIntegerEncoding();
    }
    return serialized;
  }

  /*
   * Callback on having recovered the table data from the stash during warm
   * initialisation. Populates the local state table. Note that sequence
   * identifiers are normalised in order to avoid wrapping the initial sequence
   * set. This makes it easier to generate the sorted retransmit order.
   */
  private synchronized boolean completeTableLoading(final short[] serialized) {
    if (serialized.length % 3 != 0) {
      throw new IllegalArgumentException("Invalid length for serialized data array");
    }
    stateTable = new TreeMap<Short, HandshakeInfo>();
    short sequenceIdUnwrap = 0;
    nextSequenceId = 0;
    for (int i = 0; i < serialized.length; i += 3) {
      final short packetId = serialized[i];
      short sequenceId = serialized[i + 1];
      final HandshakeState handshakeState = HandshakeState.getHandshakeState(serialized[i + 2]);
      if (i == 0) {
        sequenceIdUnwrap = sequenceId;
      }
      sequenceId -= sequenceIdUnwrap;
      if (sequenceId >= nextSequenceId) {
        nextSequenceId = (short) (sequenceId + 1);
      }
      stateTable.put(packetId, new HandshakeInfo(handshakeState, packetId, sequenceId));
      if (activePacketIds != null) {
        activePacketIds.add(packetId);
      }
    }
    return true;
  }

  /*
   * Callback on having registered a new table data stash object during cold
   * initialisation. Creates an empty local state table.
   */
  private synchronized boolean completeTableRegistration(final StashObjectEntry<short[]> stashObjectEntry) {
    stateTable = new TreeMap<Short, HandshakeInfo>();
    stashEntry = stashObjectEntry;
    return true;
  }

  /*
   * Callback handler used on completing the process of writing new packet data to
   * the stash. This is a two phase callback, where the second phase involves
   * updating the handshake state table with the new transaction state.
   */
  private class PacketDataWriteCompleteHandler implements Deferrable<Boolean, Void> {
    private final short packetId;
    private final HandshakeState handshakeState;
    private final Deferred<HandshakeInfo> deferredInsert;
    private HandshakeInfo handshake = null;

    private PacketDataWriteCompleteHandler(final short packetId, final HandshakeState handshakeState,
        final Deferred<HandshakeInfo> deferredInsert) {
      this.packetId = packetId;
      this.handshakeState = handshakeState;
      this.deferredInsert = deferredInsert;
    }

    @Override
    public Void onCallback(final Deferred<Boolean> deferred, final Boolean data) {
      synchronized (HandshakeTable.this) {
        if (handshake == null) {
          handshake = new HandshakeInfo(handshakeState, packetId, nextSequenceId++);
          stateTable.put(packetId, handshake);
          stashEntry.setCurrentValue(serializeTable()).addDeferrable(this, true);
        } else {
          deferredInsert.callback(handshake);
        }
        return null;
      }
    }

    @Override
    public Void onErrback(final Deferred<Boolean> deferred, final Exception error) {
      deferredInsert.errback(error);
      return null;
    }
  }
}
