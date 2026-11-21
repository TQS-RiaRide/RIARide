package com.riaride.fleet.internal.domain;

import static org.assertj.core.api.Assertions.assertThat;

import app.getxray.xray.junit.customjunitxml.annotations.Requirement;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

// Unit test (Surefire): BR12 applied to a vehicle. A vehicle can be booked only when it is
// available and its battery is at or above the minimum; the battery never changes the status.
class VehicleTest {

	private static final BookingBatteryPolicy POLICY = new BookingBatteryPolicy(20);
	private static final UUID HUB = UUID.fromString("00000000-0000-0000-0000-00000000a001");
	private static final Instant READ_AT = Instant.parse("2026-11-30T10:00:00Z");

	private static Vehicle availableWithBattery(int percent) {
		var vehicle = Vehicle.register("EB-601", VehicleType.E_BIKE, HUB);
		vehicle.recordBattery(BatteryLevel.of(percent), READ_AT);
		return vehicle;
	}

	@Requirement("RIA-59")
	@ParameterizedTest
	@ValueSource(ints = { 20, 21 })
	void shouldBeBookableWhenAvailableWithBatteryAtOrAbove20Percent(int percent) {
		var vehicle = availableWithBattery(percent);

		assertThat(vehicle.isBookable(POLICY)).isTrue();
	}

	@Requirement("RIA-59")
	@ParameterizedTest
	@ValueSource(ints = { 18, 19 })
	void shouldNotBeBookableWhenBatteryBelow20Percent(int percent) {
		var vehicle = availableWithBattery(percent);

		assertThat(vehicle.isBookable(POLICY)).isFalse();
	}

}
