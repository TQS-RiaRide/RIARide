package com.riaride.fleet.internal.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.getxray.xray.junit.customjunitxml.annotations.Requirement;
import com.riaride.TestcontainersConfiguration;
import com.riaride.fleet.internal.domain.BatteryLevel;
import com.riaride.fleet.internal.domain.Hub;
import com.riaride.fleet.internal.domain.HubKind;
import com.riaride.fleet.internal.domain.Vehicle;
import com.riaride.fleet.internal.domain.VehicleStatus;
import com.riaride.fleet.internal.domain.VehicleType;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

// Integration test (Failsafe): the vehicle table (Flyway V5) and its constraints on PostgreSQL in
// Testcontainers.
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
class VehicleRepositoryIT {

	private static final Instant READ_AT = Instant.parse("2026-11-30T10:00:00Z");

	@Autowired
	private VehicleRepository vehicles;

	@Autowired
	private HubRepository hubs;

	@Autowired
	private JdbcTemplate jdbc;

	private UUID hubId;

	@BeforeEach
	void registerHub() {
		var hub = Hub.register("Miradouro da Ria", HubKind.TOURIST_SPOT, null, 40.64, -8.65, 6);
		hubs.saveAndFlush(hub);
		hubId = hub.getId();
	}

	@Requirement("RIA-59")
	@Test
	void shouldPersistBatteryReadingAndFindVehicleByCode() {
		var vehicle = Vehicle.register("EB-601", VehicleType.E_BIKE, hubId);
		vehicle.recordBattery(BatteryLevel.of(18), READ_AT);

		vehicles.saveAndFlush(vehicle);

		assertThat(vehicles.findByCode("EB-601"))
			.get()
			.satisfies(found -> {
				assertThat(found.getStatus()).isEqualTo(VehicleStatus.AVAILABLE);
				assertThat(found.battery()).isEqualTo(BatteryLevel.of(18));
				assertThat(found.getLastTelemetryAt()).isEqualTo(READ_AT);
			});
	}

	@Requirement("RIA-59")
	@Test
	void shouldListTheVehiclesDockedAtAHubOrderedByCode() {
		vehicles.saveAndFlush(Vehicle.register("EB-602", VehicleType.E_BIKE, hubId));
		vehicles.saveAndFlush(Vehicle.register("EB-601", VehicleType.E_BIKE, hubId));

		assertThat(vehicles.findByHubIdOrderByCode(hubId))
			.extracting(Vehicle::getCode)
			.containsExactly("EB-601", "EB-602");
	}

	@Requirement("RIA-59")
	@Test
	void shouldRejectTwoVehiclesWithTheSameCode() {
		vehicles.saveAndFlush(Vehicle.register("EB-601", VehicleType.E_BIKE, hubId));

		assertThatThrownBy(() -> vehicles.saveAndFlush(Vehicle.register("EB-601", VehicleType.E_BIKE, hubId)))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Requirement("RIA-59")
	@Test
	void shouldRejectBatteryOutside0To100PercentInTheDatabase() {
		var vehicle = Vehicle.register("EB-601", VehicleType.E_BIKE, hubId);
		vehicles.saveAndFlush(vehicle);

		assertThatThrownBy(() -> jdbc.update("update vehicle set battery_pct = 101 where id = ?", vehicle.getId()))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

}
