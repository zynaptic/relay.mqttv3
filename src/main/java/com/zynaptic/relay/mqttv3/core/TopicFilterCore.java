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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.zynaptic.relay.mqttv3.QosPolicyType;
import com.zynaptic.relay.mqttv3.TopicFilter;

/**
 * Implements the core functionality of a single topic filter.
 *
 * @author Chris Holgate
 */
final class TopicFilterCore implements TopicFilter {

  private final ValidatedString topicString;
  private final QosPolicyType topicQos;
  private final String filterRegex;
  private Pattern regexPattern = null;

  /**
   * Protected constructor used by client connection objects to create new topic
   * filters.
   *
   * @param topicString This is a Java native string which should contain a valid
   *   MQTT topic filter expression.
   * @param topicQos This is the maximum QoS level which is to be used in
   *   conjunction with the topic filter.
   */
  TopicFilterCore(final String topicString, final QosPolicyType topicQos) {
    if ((topicString == null) || (topicString.length() == 0)) {
      throw new IllegalArgumentException("Topic filter strings must contain at least one character");
    }
    if ((topicQos == null) || (topicQos == QosPolicyType.INVALID)) {
      throw new IllegalArgumentException("Invalid QoS policy type");
    }
    try {
      this.topicString = ValidatedString.create(topicString);
    } catch (final CharacterCodingException error) {
      throw new IllegalArgumentException("Topic filter string contains invalid characters", error);
    }
    this.topicQos = topicQos;
    filterRegex = buildRegex(topicString);
  }

  /*
   * Implements TopicFilter.getFilterString();
   */
  @Override
  public String getFilterString() {
    return topicString.getString();
  }

  /*
   * Implements TopicFilter.getFilterRegex()
   */
  @Override
  public String getFilterRegex() {
    return filterRegex;
  }

  /*
   * Implements TopicFilter.getQosPolicy()
   */
  @Override
  public QosPolicyType getQosPolicy() {
    return topicQos;
  }

  /*
   * Implements TopicFilter.matches(...)
   */
  @Override
  public synchronized boolean matches(final String topicName) {
    if (regexPattern == null) {
      regexPattern = Pattern.compile(filterRegex);
    }
    final Matcher matcher = regexPattern.matcher(topicName);
    return matcher.matches();
  }

  /**
   * Accesses the UTF-8 encoded byte array representation of the topic filter.
   * This includes the two byte length prefix.
   *
   * @return Returns the UTF-8 encoded representation of the topic filter.
   */
  byte[] getFilterByteEncoding() {
    return topicString.getByteEncoding();
  }

  /*
   * Parse the provided topic filter string and construct a Java regular
   * expression that may be used to detect topic filter matches.
   */
  private String buildRegex(final String topicFilter) {
    final String[] filterSegments = topicFilter.split("/");
    final StringBuilder regexBuilder = new StringBuilder("(^");
    for (int i = 0; i < filterSegments.length; i++) {

      // Process multi-level wildcards. These must always be the last character in the
      // topic filter.
      if (filterSegments[i].equals("#")) {
        if (i != filterSegments.length - 1) {
          throw new IllegalArgumentException("Invalid use of '#' wildcard character in topic filter");
        }
        if (i == 0) {
          regexBuilder.append("([^/\\+\\#\\$][^/]*)?(/[^/\\+\\#]*)*");
        } else {
          regexBuilder.append("(/[^/\\+\\#]*)+");
        }
      }

      // Process single level wildcards. These must always be a single character that
      // matches an entire topic level.
      else if (filterSegments[i].equals("+")) {
        if (i == 0) {
          regexBuilder.append("([^/\\+\\#\\$][^/\\+\\#]*)?");
        } else {
          regexBuilder.append("/[^/\\+\\#]*");
        }
      }

      // Process single level literal string, including escaping all non-alphabetic
      // characters.
      else if ((filterSegments[i].indexOf('#') < 0) && (filterSegments[i].indexOf('+') < 0)) {
        if (i != 0) {
          regexBuilder.append('/');
        }
        for (int j = 0; j < filterSegments[i].length(); j++) {
          final char nextChar = filterSegments[i].charAt(j);
          if (((nextChar >= 'A') && (nextChar <= 'Z')) || ((nextChar >= 'a') && (nextChar <= 'z'))) {
            regexBuilder.append(nextChar);
          } else {
            regexBuilder.append('\\').append(nextChar);
          }
        }
      }

      // Catch invalid use of wildcard characters.
      else {
        throw new IllegalArgumentException("Invalid use of wildcard characters in topic filter");
      }
    }
    regexBuilder.append("$)");
    return regexBuilder.toString();
  }
}
