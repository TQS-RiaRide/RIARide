package com.riaride.telemetry.internal.mqtt;

// A message on a telemetry topic that is not a valid reading. The adapter logs and drops it, so
// one bad message never stops the subscription.
class InvalidTelemetryMessageException extends RuntimeException {

	InvalidTelemetryMessageException(String message) {
		super(message);
	}

	InvalidTelemetryMessageException(String message, Throwable cause) {
		super(message, cause);
	}

}
