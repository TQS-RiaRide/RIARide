package com.riaride.telemetry.internal.mqtt;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;

// Publishes each telemetry message as a VehicleTelemetryReceived event, in one transaction, so the
// listeners store the reading before the message is acknowledged (QoS 1). A message that is not a
// valid reading is logged and dropped: redelivering it would fail again.
class TelemetryMessageHandler {

  private static final Logger log = LoggerFactory.getLogger(TelemetryMessageHandler.class);

  private final TelemetryMessageMapper mapper;
  private final ApplicationEventPublisher events;

  TelemetryMessageHandler(TelemetryMessageMapper mapper, ApplicationEventPublisher events) {
    this.mapper = mapper;
    this.events = events;
  }

  @Transactional
  public void handle(String topic, byte[] payload) {
    try {
      events.publishEvent(mapper.map(topic, payload));
    } catch (InvalidTelemetryMessageException invalid) {
      // The payload is not logged: it is not ours to trust or to keep.
      log.warn(
          "Invalid telemetry message on {} dropped: {}",
          LogSafeTopic.of(topic),
          invalid.getMessage());
    }
  }
}
