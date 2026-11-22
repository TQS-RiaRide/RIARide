package com.riaride.fleet.internal.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.getxray.xray.junit.customjunitxml.annotations.Requirement;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

// Unit test (Surefire): an operator registers a vehicle with a code and a type at a hub (RIA-57).
// A new vehicle is docked there, available, with no battery reading yet. The code follows the MQTT
// topic rule (1 to 32 letters, digits or hyphens). Whether the hub exists and has a free slot
// (BR10) needs the other vehicles, so it is tested in RegisterVehicleServiceTest and the API test.
class VehicleRegistrationTest {

	private static final UUID HUB = UUID.fromString("00000000-0000-0000-0000-00000000a057");

	private static void assertRejectsField(Runnable registration, String field) {
		assertThatThrownBy(registration::run)
			.isInstanceOfSatisfying(InvalidVehicleException.class,
				invalid -> assertThat(invalid.getField()).isEqualTo(field));
	}

	@Requirement("RIA-57")
	@Test
	void shouldRegisterVehicleDockedAtHubAndAvailableWithNoBatteryReading() {
		var vehicle = Vehicle.register("EB-401", VehicleType.E_BIKE, HUB);

		assertThat(vehicle.getId()).isNotNull();
		assertThat(vehicle.getCode()).isEqualTo("EB-401");
		assertThat(vehicle.getType()).isEqualTo(VehicleType.E_BIKE);
		assertThat(vehicle.getStatus()).isEqualTo(VehicleStatus.AVAILABLE);
		assertThat(vehicle.getHubId()).isEqualTo(HUB);
		assertThat(vehicle.battery()).isNull();
		assertThat(vehicle.isNew()).isTrue();
	}

	@Requirement("RIA-57")
	@ParameterizedTest
	@ValueSource(strings = { "E", "EB-401", "eb-401", "123", "ABCDEFGHIJKLMNOPQRSTUVWXYZ-12345" })
	void shouldAcceptCodesOfLettersDigitsAndHyphensUpTo32Characters(String code) {
		assertThat(Vehicle.register(code, VehicleType.E_BIKE, HUB).getCode()).isEqualTo(code);
	}

	@Requirement("RIA-57")
	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = {
		"ABCDEFGHIJKLMNOPQRSTUVWXYZ-123456", // 33 characters
		"EB 401", " EB-401", "EB/401", "EB+", "EB#1", "EB_401", "EB-4Ø1", "EB​401" })
	void shouldRejectCodesThatCannotBeAnMqttTopicLevel(String code) {
		assertRejectsField(() -> Vehicle.register(code, VehicleType.E_BIKE, HUB), "code");
	}

	@Requirement("RIA-57")
	@Test
	void shouldRejectVehicleWithoutType() {
		assertRejectsField(() -> Vehicle.register("EB-401", null, HUB), "type");
	}

	@Requirement("RIA-57")
	@Test
	void shouldRejectVehicleWithoutHub() {
		assertRejectsField(() -> Vehicle.register("EB-401", VehicleType.E_BIKE, null), "hub");
	}

	@Requirement("RIA-57")
	@Test
	void shouldNotRepeatTheRejectedCodeInTheMessage() {
		assertThatThrownBy(() -> Vehicle.register("EB/SECRET", VehicleType.E_BIKE, HUB))
			.hasMessageNotContaining("SECRET");
	}

}
