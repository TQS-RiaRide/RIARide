package com.riaride.fleet.internal.domain;

import static org.assertj.core.api.Assertions.assertThat;

import app.getxray.xray.junit.customjunitxml.annotations.Requirement;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

// Unit test (Surefire): BR12 applied to a vehicle. A vehicle can be booked only when it is
// available and its battery is at or above the minimum; the battery never changes the status.
class VehicleTest {

	private static final BookingBatteryPolicy POLICY = new BookingBatteryPolicy(20);
	private static final UUID HUB = UUID.fromString("00000000-0000-0000-0000-00000000a001");
	private static final Instant READ_AT = Instant.parse("2026-11-30T10:00:00Z");

	private static Vehicle availableWithBattery(int percent) {
		var vehicle = Vehicle.register("EB-601", VehicleType.E_BIKE, HUB);
		vehicle.recordBattery(BatteryLevel.of(percent), READ_AT, READ_AT);
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

	@Requirement("RIA-59")
	@ParameterizedTest
	@EnumSource(value = VehicleStatus.class, names = "AVAILABLE", mode = EnumSource.Mode.EXCLUDE)
	void shouldNotBeBookableWhenNotAvailableEvenWithFullBattery(VehicleStatus status) {
		var vehicle = Vehicle.withStatus("EB-601", VehicleType.E_BIKE, HUB, status);
		vehicle.recordBattery(BatteryLevel.of(100), READ_AT, READ_AT);

		assertThat(vehicle.isBookable(POLICY)).isFalse();
	}

	@Requirement("RIA-59")
	@Test
	void shouldKeepStatusAvailableWhenBatteryDropsBelow20Percent() {
		var vehicle = availableWithBattery(80);

		vehicle.recordBattery(BatteryLevel.of(18), READ_AT.plusSeconds(5), READ_AT.plusSeconds(5));

		assertThat(vehicle.getStatus()).isEqualTo(VehicleStatus.AVAILABLE);
		assertThat(vehicle.battery()).isEqualTo(BatteryLevel.of(18));
		assertThat(vehicle.getLastTelemetryAt()).isEqualTo(READ_AT.plusSeconds(5));
	}

	@Requirement("RIA-59")
	@Test
	void shouldBeBookableAgainWhenChargedBackTo20Percent() {
		var vehicle = availableWithBattery(18);

		vehicle.recordBattery(BatteryLevel.of(20), READ_AT.plusSeconds(60), READ_AT.plusSeconds(60));

		assertThat(vehicle.isBookable(POLICY)).isTrue();
	}

	@Requirement("RIA-59")
	@Test
	void shouldNotBeBookableBeforeTheFirstBatteryReading() {
		var vehicle = Vehicle.register("EB-601", VehicleType.E_BIKE, HUB);

		assertThat(vehicle.isBookable(POLICY)).isFalse();
	}

	@Requirement("RIA-59")
	@Test
	void shouldIgnoreAReadingOlderThanTheLastOne() {
		var vehicle = availableWithBattery(80);
		vehicle.recordBattery(BatteryLevel.of(18), READ_AT.plusSeconds(10), READ_AT.plusSeconds(10));

		vehicle.recordBattery(BatteryLevel.of(90), READ_AT.plusSeconds(5), READ_AT.plusSeconds(5));

		assertThat(vehicle.battery()).isEqualTo(BatteryLevel.of(18));
		assertThat(vehicle.getLastTelemetryAt()).isEqualTo(READ_AT.plusSeconds(10));
		assertThat(vehicle.isBookable(POLICY)).isFalse();
	}

	@Requirement("RIA-59")
	@Test
	void shouldAcceptAReadingUpToOneMinuteAheadOfTheServerClock() {
		var vehicle = availableWithBattery(80);

		vehicle.recordBattery(BatteryLevel.of(18), READ_AT.plusSeconds(60), READ_AT);

		assertThat(vehicle.battery()).isEqualTo(BatteryLevel.of(18));
		assertThat(vehicle.getLastTelemetryAt()).isEqualTo(READ_AT.plusSeconds(60));
	}

	@Requirement("RIA-59")
	@Test
	void shouldIgnoreAReadingMoreThanOneMinuteAheadOfTheServerClock() {
		var vehicle = availableWithBattery(80);

		vehicle.recordBattery(BatteryLevel.of(18), READ_AT.plusSeconds(61), READ_AT);

		assertThat(vehicle.battery()).isEqualTo(BatteryLevel.of(80));
		assertThat(vehicle.getLastTelemetryAt()).isEqualTo(READ_AT);
	}

	@Requirement("RIA-59")
	@Test
	void shouldKeepAcceptingReadingsAfterIgnoringOneFromTheFuture() {
		var vehicle = availableWithBattery(80);
		vehicle.recordBattery(BatteryLevel.of(90), READ_AT.plusSeconds(86_400), READ_AT);

		vehicle.recordBattery(BatteryLevel.of(18), READ_AT.plusSeconds(10), READ_AT.plusSeconds(10));

		assertThat(vehicle.battery()).isEqualTo(BatteryLevel.of(18));
		assertThat(vehicle.isBookable(POLICY)).isFalse();
	}

}
