package com.riaride.trips.internal.domain;

import static org.assertj.core.api.Assertions.assertThat;

import app.getxray.xray.junit.customjunitxml.annotations.Requirement;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

// Unit test (Surefire): a trip is STARTING until telemetry confirms the unlock; without that
// confirmation within 30 seconds it FAILS (RIA-54). Time comes from a fixed Clock, offset per case.
class TripTest {

	private static final Instant REQUESTED_AT = Instant.parse("2026-11-30T10:00:00Z");
	private static final Duration UNLOCK_TIMEOUT = Duration.ofSeconds(30);
	private static final UUID GUEST = UUID.fromString("6a1e7f3b-4c2d-4e8f-9a0b-1c2d3e4f5a60");
	private static final UUID PASS = UUID.fromString("0ca7add9-0d29-380f-80b5-f8bed8397bea");
	private static final UUID VEHICLE = UUID.fromString("00000000-0000-0000-0000-0000000e0201");

	private static Trip startingTrip() {
		return Trip.requestStart(GUEST, PASS, VEHICLE, "EB-201", REQUESTED_AT);
	}

	// The instant a Clock fixed at the request time, moved forward by the given seconds, reports.
	private static Instant secondsAfterRequest(long seconds) {
		return Clock.offset(Clock.fixed(REQUESTED_AT, ZoneOffset.UTC), Duration.ofSeconds(seconds)).instant();
	}

	@Requirement("RIA-54")
	@Test
	void shouldBeStartingWhenRequested() {
		var trip = startingTrip();

		assertThat(trip.getStatus()).isEqualTo(TripStatus.STARTING);
		assertThat(trip.getGuestId()).isEqualTo(GUEST);
		assertThat(trip.getPassId()).isEqualTo(PASS);
		assertThat(trip.getVehicleId()).isEqualTo(VEHICLE);
		assertThat(trip.getVehicleCode()).isEqualTo("EB-201");
		assertThat(trip.getRequestedAt()).isEqualTo(REQUESTED_AT);
		assertThat(trip.getStartedAt()).isNull();
	}

	@Requirement("RIA-54")
	@ParameterizedTest(name = "{0} s after the request, overdue = {1}")
	@CsvSource({ "29, false", "30, true", "31, true" })
	void shouldBeOverdueFrom30SecondsWithoutUnlock(long seconds, boolean overdue) {
		var trip = startingTrip();

		assertThat(trip.isUnlockOverdue(secondsAfterRequest(seconds), UNLOCK_TIMEOUT)).isEqualTo(overdue);
	}

	@Requirement("RIA-54")
	@Test
	void shouldStartWhenTheUnlockIsConfirmedBefore30Seconds() {
		var trip = startingTrip();

		var started = trip.confirmUnlock(secondsAfterRequest(29), UNLOCK_TIMEOUT);

		assertThat(started).isTrue();
		assertThat(trip.getStatus()).isEqualTo(TripStatus.IN_USE);
		assertThat(trip.getStartedAt()).isEqualTo(secondsAfterRequest(29));
	}

	// An unlock reported once the 30 seconds are over does not start the trip: it fails instead.
	@Requirement("RIA-54")
	@ParameterizedTest
	@ValueSource(longs = { 30, 31 })
	void shouldNotStartWhenTheUnlockIsConfirmedFrom30Seconds(long seconds) {
		var trip = startingTrip();

		var started = trip.confirmUnlock(secondsAfterRequest(seconds), UNLOCK_TIMEOUT);

		assertThat(started).isFalse();
		assertThat(trip.getStatus()).isNotEqualTo(TripStatus.IN_USE);
		assertThat(trip.getStartedAt()).isNull();
	}

	@Requirement("RIA-54")
	@ParameterizedTest(name = "{0} s after the request, failed = {1}")
	@CsvSource({ "29, false", "30, true", "31, true" })
	void shouldFailOnlyFrom30SecondsWithoutUnlock(long seconds, boolean failed) {
		var trip = startingTrip();

		var result = trip.failIfUnlockOverdue(secondsAfterRequest(seconds), UNLOCK_TIMEOUT);

		assertThat(result).isEqualTo(failed);
		assertThat(trip.getStatus()).isEqualTo(failed ? TripStatus.FAILED : TripStatus.STARTING);
	}

	@Requirement("RIA-54")
	@Test
	void shouldNotFailATripAlreadyInUse() {
		var trip = startingTrip();
		trip.confirmUnlock(secondsAfterRequest(10), UNLOCK_TIMEOUT);

		var result = trip.failIfUnlockOverdue(secondsAfterRequest(60), UNLOCK_TIMEOUT);

		assertThat(result).isFalse();
		assertThat(trip.getStatus()).isEqualTo(TripStatus.IN_USE);
	}

	// The simulator reports the unlocked state periodically while the bike is ridden.
	@Requirement("RIA-54")
	@Test
	void shouldIgnoreARepeatedUnlockOnceInUse() {
		var trip = startingTrip();
		trip.confirmUnlock(secondsAfterRequest(10), UNLOCK_TIMEOUT);

		var startedAgain = trip.confirmUnlock(secondsAfterRequest(20), UNLOCK_TIMEOUT);

		assertThat(startedAgain).isFalse();
		assertThat(trip.getStatus()).isEqualTo(TripStatus.IN_USE);
		assertThat(trip.getStartedAt()).isEqualTo(secondsAfterRequest(10));
	}

	@Requirement("RIA-54")
	@Test
	void shouldNotStartAFailedTrip() {
		var trip = startingTrip();
		trip.failIfUnlockOverdue(secondsAfterRequest(30), UNLOCK_TIMEOUT);

		var started = trip.confirmUnlock(secondsAfterRequest(31), UNLOCK_TIMEOUT);

		assertThat(started).isFalse();
		assertThat(trip.getStatus()).isEqualTo(TripStatus.FAILED);
	}

	@Requirement("RIA-54")
	@Test
	void shouldCountStartingAndInUseAsActive() {
		assertThat(TripStatus.ACTIVE).containsExactlyInAnyOrder(TripStatus.STARTING, TripStatus.IN_USE);
	}

}
