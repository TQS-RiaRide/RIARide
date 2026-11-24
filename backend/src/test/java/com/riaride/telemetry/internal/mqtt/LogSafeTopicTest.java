package com.riaride.telemetry.internal.mqtt;

import static org.assertj.core.api.Assertions.assertThat;

import app.getxray.xray.junit.customjunitxml.annotations.Requirement;
import org.junit.jupiter.api.Test;

// Unit test (Surefire): a topic comes from whoever publishes on the broker, and MQTT allows CR and LF
// in a topic level, so it is escaped before it reaches the log (no forged log lines).
class LogSafeTopicTest {

	@Requirement("RIA-59")
	@Test
	void shouldKeepAPlainTopicAsItIs() {
		assertThat(LogSafeTopic.of("riaride/vehicles/EB-601/telemetry")).isEqualTo("riaride/vehicles/EB-601/telemetry");
	}

	@Requirement("RIA-59")
	@Test
	void shouldReplaceControlCharactersSoATopicCannotForgeALogLine() {
		assertThat(LogSafeTopic.of("riaride/vehicles/EB\r\n2026 INFO forged/telemetry"))
			.isEqualTo("riaride/vehicles/EB??2026 INFO forged/telemetry");
	}

	@Requirement("RIA-59")
	@Test
	void shouldShortenAVeryLongTopic() {
		var topic = "riaride/vehicles/" + "x".repeat(500) + "/telemetry";

		assertThat(LogSafeTopic.of(topic)).hasSize(LogSafeTopic.MAX_LENGTH + 3).endsWith("...");
	}

}
