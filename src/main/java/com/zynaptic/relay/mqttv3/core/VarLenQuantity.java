/*
 * Zynaptic Relay MQTTv3 - An MQTT version 3.1.1 implementation for Java.
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

/**
 * Implements the variable length quantity (VLQ) data type for encoding MQTT
 * packet lengths.
 *
 * @author Chris Holgate
 */
final class VarLenQuantity {

  private final int value;
  private final byte[] byteEncoding;

  /**
   * Provides private constructor for generating new instances via the static
   * create methods.
   *
   * @param value This is the integer value which is encoded as a variable length
   *   quantity.
   * @param byteEncoding This is the byte encoding of the variable length
   *   quantity.
   */
  private VarLenQuantity(final int value, final byte[] byteEncoding) {
    this.value = value;
    this.byteEncoding = byteEncoding;
  }

  /**
   * Static method for creating a new variable length quantity from the encoded
   * value located at the current position of a byte buffer.
   *
   * @param byteBuffer This is the byte buffer which contains the encoded variable
   *   length quantity.
   * @return Returns a newly created variable length quantity, generated by
   *   parsing the input byte buffer.
   * @throws MalformedPacketException This exception will be thrown if data at the
   *   current position of the byte buffer is not a valid variable length quantity
   *   encoding.
   * @throws BufferUnderflowException This exception will be thrown if a buffer
   *   underflow was encountered while parsing the variable length quantity.
   */
  static VarLenQuantity create(final ByteBuffer byteBuffer) throws MalformedPacketException, BufferUnderflowException {
    final int startPosition = byteBuffer.position();
    int length = 0;
    int shift = 0;
    int value = 0;
    int byteVal;
    do {
      if (shift > 21) {
        throw new MalformedPacketException("Invalid variable length encoding format");
      }
      byteVal = byteBuffer.get();
      value += (byteVal & 0x7F) << shift;
      shift += 7;
      length += 1;
    } while (byteVal < 0);
    final byte[] byteEncoding = new byte[length];
    byteBuffer.position(startPosition).get(byteEncoding);
    return new VarLenQuantity(value, byteEncoding);
  }

  /**
   * Static method for creating a new variable length quantity from an integer
   * value.
   *
   * @param value This is the integer value which is to be encoded as a variable
   *   length quantity. It should be in the valid range for a four byte encoding
   *   of of 0 to 0xFFFFFFF.
   * @return Returns a newly created variable length quantity, generated by
   *   encoding the integer value.
   */
  static VarLenQuantity create(int value) {
    byte[] byteEncoding;
    if ((value > 0xFFFFFFF) || (value < 0)) {
      throw new IllegalArgumentException(
          "Variable length integer value " + value + " is outside valid range of 0 to 0xFFFFFFF");
    } else if (value <= 0x7F) {
      return new VarLenQuantity(value, new byte[] { (byte) value });
    } else if (value <= 0x3FFF) {
      byteEncoding = new byte[2];
    } else if (value <= 0x1FFFFF) {
      byteEncoding = new byte[3];
    } else {
      byteEncoding = new byte[4];
    }
    for (int i = 0; i < byteEncoding.length; i++) {
      int byteVal = value & 0x7F;
      value >>= 7;
      if (value != 0) {
        byteVal |= 0x80;
      }
      byteEncoding[i] = (byte) byteVal;
    }
    return new VarLenQuantity(value, byteEncoding);
  }

  /**
   * Accesses the integer value that is encoded as the variable length quantity.
   *
   * @return Returns the integer value for the variable length quantity..
   */
  int getValue() {
    return value;
  }

  /**
   * Accesses the byte encoding for the variable length quantity.
   *
   * @return Returns the byte encoding for the variable length quantity.
   */
  byte[] getByteEncoding() {
    return byteEncoding;
  }
}
