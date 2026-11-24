package com.riaride.telemetry.internal.mqtt;

// An MQTT topic as it may be logged. Anyone who can publish on the broker chooses the topic, and
// MQTT
// allows control characters such as CR and LF in a topic level, so they are replaced and the length
// is capped: a topic can neither forge log lines nor flood the log.
final class LogSafeTopic {

  static final int MAX_LENGTH = 128;

  private LogSafeTopic() {}

  static String of(String topic) {
    var safe =
        topic
            .codePoints()
            .map(c -> Character.isISOControl(c) ? '?' : c)
            .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
            .toString();
    return safe.length() <= MAX_LENGTH ? safe : safe.substring(0, MAX_LENGTH) + "...";
  }
}
