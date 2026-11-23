package com.riaride.fleet.internal.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import app.getxray.xray.junit.customjunitxml.annotations.Requirement;
import com.riaride.fleet.internal.domain.BatteryLevel;
import com.riaride.fleet.internal.domain.Vehicle;
import com.riaride.fleet.internal.domain.VehicleStatus;
import com.riaride.fleet.internal.domain.VehicleType;
import com.riaride.fleet.internal.persistence.VehicleRepository;
import com.riaride.telemetry.VehicleTelemetryReceived;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

// Unit test (Surefire): the fleet keeps each vehicle's latest battery from telemetry (RIA-59).
class VehicleTelemetryListenerTest {

	private static final Instant EMITTED_AT = Instant.parse("2026-11-30T10:00:05Z");

	private final VehicleRepository vehicles = mock(VehicleRepository.class);
	private final VehicleTelemetryListener listener = new VehicleTelemetryListener(vehicles,
		Clock.fixed(EMITTED_AT, ZoneOffset.UTC));

	private static VehicleTelemetryReceived reading(String code, int batteryPct) {
		return new VehicleTelemetryReceived(code, "3f6c1d2e-7a4b-4c1d-9e2f-0a1b2c3d4e5f", EMITTED_AT, batteryPct,
			null, "LOCKED");
	}

	@Requirement("RIA-59")
	@Test
	void shouldRecordTheReportedBatteryAndKeepTheStatus() {
		var vehicle = Vehicle.register("EB-601", VehicleType.E_BIKE, UUID.randomUUID());
		vehicle.recordBattery(BatteryLevel.of(80), EMITTED_AT.minusSeconds(5), EMITTED_AT.minusSeconds(5));
		when(vehicles.findByCodeForUpdate("EB-601")).thenReturn(Optional.of(vehicle));

		listener.on(reading("EB-601", 18));

		assertThat(vehicle.battery()).isEqualTo(BatteryLevel.of(18));
		assertThat(vehicle.getLastTelemetryAt()).isEqualTo(EMITTED_AT);
		assertThat(vehicle.getStatus()).isEqualTo(VehicleStatus.AVAILABLE);
	}

	@Requirement("RIA-59")
	@Test
	void shouldIgnoreAReadingStampedADayAheadOfTheServerClock() {
		var vehicle = Vehicle.register("EB-601", VehicleType.E_BIKE, UUID.randomUUID());
		vehicle.recordBattery(BatteryLevel.of(80), EMITTED_AT.minusSeconds(5), EMITTED_AT.minusSeconds(5));
		when(vehicles.findByCodeForUpdate("EB-601")).thenReturn(Optional.of(vehicle));
		var fromTheFuture = new VehicleTelemetryReceived("EB-601", "9a1b2c3d-4e5f-4a6b-8c7d-0e1f2a3b4c5d",
			EMITTED_AT.plusSeconds(86_400), 18, null, "LOCKED");

		listener.on(fromTheFuture);

		assertThat(vehicle.battery()).isEqualTo(BatteryLevel.of(80));
		assertThat(vehicle.getLastTelemetryAt()).isEqualTo(EMITTED_AT.minusSeconds(5));
	}

	@Requirement("RIA-59")
	@Test
	void shouldIgnoreAReadingForAnUnknownVehicle() {
		when(vehicles.findByCodeForUpdate("EB-999")).thenReturn(Optional.empty());

		assertThatCode(() -> listener.on(reading("EB-999", 18))).doesNotThrowAnyException();
	}

}
