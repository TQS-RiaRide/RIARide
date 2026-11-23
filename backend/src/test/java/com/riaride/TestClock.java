package com.riaride;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.context.annotation.Primary;

// A clock the acceptance tests move by hand, so time rules such as the 30-second unlock timeout
// (RIA-54) run without sleeps. It starts at the same instant as FixedClockConfiguration, so the
// date rules of the other scenarios do not change; reset() puts it back before each scenario.
// Thread-safe: the application reads it from request, MQTT and scheduler threads.
@Primary
public class TestClock extends Clock {

	private final AtomicReference<Instant> now;
	private final ZoneId zone;

	public TestClock() {
		this(new AtomicReference<>(FixedClockConfiguration.NOW), FixedClockConfiguration.VILA_RIA);
	}

	private TestClock(AtomicReference<Instant> now, ZoneId zone) {
		this.now = now;
		this.zone = zone;
	}

	public void advance(Duration duration) {
		now.updateAndGet(instant -> instant.plus(duration));
	}

	public void reset() {
		now.set(FixedClockConfiguration.NOW);
	}

	@Override
	public Instant instant() {
		return now.get();
	}

	@Override
	public ZoneId getZone() {
		return zone;
	}

	// The same time in another zone: both clocks move together.
	@Override
	public Clock withZone(ZoneId otherZone) {
		return new TestClock(now, otherZone);
	}

}
