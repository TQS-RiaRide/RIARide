package com.riaride.trips.internal.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.getxray.xray.junit.customjunitxml.annotations.Requirement;
import com.riaride.TestcontainersConfiguration;
import com.riaride.trips.internal.domain.Trip;
import com.riaride.trips.internal.domain.TripStatus;
import java.time.Duration;
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

// Integration test (Failsafe): the trip table (Flyway V6) on PostgreSQL in Testcontainers. The
// database itself guarantees one trip in progress per pass (BR6), so two concurrent starts cannot
// both succeed (RIA-54).
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
class TripRepositoryIT {

	private static final Instant NOW = Instant.parse("2026-11-30T10:00:00Z");
	private static final Duration UNLOCK_TIMEOUT = Duration.ofSeconds(30);
	private static final String MARES_CENTRO = "00000000-0000-0000-0000-000000000001";

	@Autowired
	private TripRepository trips;

	@Autowired
	private JdbcTemplate jdbc;

	private UUID guestId;
	private UUID passId;
	private UUID bike201;
	private UUID bike202;

	@BeforeEach
	void guestWithAPassAndTwoBikes() {
		var stayId = UUID.randomUUID();
		jdbc.update("""
			insert into stay (id, hotel_id, guest_name, stay_code, check_in, check_out, status)
			values (?, ?::uuid, 'Rita Almeida', 'TRIP2345', date '2026-11-28', date '2026-12-02', 'ACTIVE')
			""", stayId, MARES_CENTRO);
		guestId = UUID.randomUUID();
		jdbc.update("""
			insert into guest (id, keycloak_id, stay_id, email, locale)
			values (?, ?, ?, 'rita.almeida@example.test', 'pt')
			""", guestId, UUID.randomUUID().toString(), stayId);
		passId = UUID.randomUUID();
		jdbc.update("""
			insert into pass (id, guest_id, pass_type, status, price_cents, deposit_cents, deposit_status)
			values (?, ?, 'STAY', 'INACTIVE', 4000, 5000, 'HELD')
			""", passId, guestId);
		var hubId = UUID.randomUUID();
		jdbc.update("""
			insert into hub (id, name, kind, hotel_id, latitude, longitude, capacity)
			values (?, 'Marés Centro', 'HOTEL', ?::uuid, 40.6405, -8.6538, 10)
			""", hubId, MARES_CENTRO);
		bike201 = bike("EB-201", hubId);
		bike202 = bike("EB-202", hubId);
	}

	private UUID bike(String code, UUID hubId) {
		var id = UUID.randomUUID();
		jdbc.update("""
			insert into vehicle (id, code, type, status, hub_id, battery_pct)
			values (?, ?, 'E_BIKE', 'AVAILABLE', ?, 80)
			""", id, code, hubId);
		return id;
	}

	private Trip startingTrip(UUID vehicleId, String code, Instant requestedAt) {
		return Trip.requestStart(guestId, passId, vehicleId, code, requestedAt);
	}

	@Requirement("RIA-54")
	@Test
	void shouldStoreAStartingTrip() {
		var trip = trips.saveAndFlush(startingTrip(bike201, "EB-201", NOW));

		assertThat(trips.findById(trip.getId()))
			.get()
			.satisfies(found -> {
				assertThat(found.getStatus()).isEqualTo(TripStatus.STARTING);
				assertThat(found.getPassId()).isEqualTo(passId);
				assertThat(found.getVehicleCode()).isEqualTo("EB-201");
				assertThat(found.getRequestedAt()).isEqualTo(NOW);
			});
	}

	@Requirement("RIA-54")
	@Test
	void shouldRefuseASecondActiveTripForTheSamePass() {
		trips.saveAndFlush(startingTrip(bike201, "EB-201", NOW));

		assertThatThrownBy(() -> trips.saveAndFlush(startingTrip(bike202, "EB-202", NOW.plusSeconds(1))))
			.isInstanceOf(DataIntegrityViolationException.class)
			.hasMessageContaining("uq_trip_active_per_pass");
	}

	@Requirement("RIA-54")
	@Test
	void shouldAllowANewTripAfterAFailedOne() {
		var failed = startingTrip(bike201, "EB-201", NOW);
		failed.failIfUnlockOverdue(NOW.plus(UNLOCK_TIMEOUT), UNLOCK_TIMEOUT);
		trips.saveAndFlush(failed);

		trips.saveAndFlush(startingTrip(bike201, "EB-201", NOW.plusSeconds(60)));

		assertThat(trips.count()).isEqualTo(2);
	}

	@Requirement("RIA-54")
	@Test
	void shouldTellWhetherAPassHasATripInProgress() {
		assertThat(trips.existsByPassIdAndStatusIn(passId, TripStatus.ACTIVE)).isFalse();

		trips.saveAndFlush(startingTrip(bike201, "EB-201", NOW));

		assertThat(trips.existsByPassIdAndStatusIn(passId, TripStatus.ACTIVE)).isTrue();
	}

	@Requirement("RIA-54")
	@Test
	void shouldFindTheStartingTripOfABike() {
		var trip = trips.saveAndFlush(startingTrip(bike201, "EB-201", NOW));

		assertThat(trips.findFirstByVehicleCodeAndStatus("EB-201", TripStatus.STARTING))
			.get()
			.extracting(Trip::getId)
			.isEqualTo(trip.getId());
		assertThat(trips.findFirstByVehicleCodeAndStatus("EB-202", TripStatus.STARTING)).isEmpty();
	}

	// The timeout job looks for trips still starting at or before the cutoff (now minus 30 s).
	@Requirement("RIA-54")
	@Test
	void shouldFindStartingTripsRequestedAtOrBeforeTheCutoff() {
		var overdue = trips.saveAndFlush(startingTrip(bike201, "EB-201", NOW.minusSeconds(30)));
		// A second pass of the same guest (an older, closed one), so both trips can be STARTING.
		var otherPass = UUID.randomUUID();
		jdbc.update("""
			insert into pass (id, guest_id, pass_type, status, price_cents, deposit_cents, deposit_status)
			values (?, ?, 'STAY', 'CLOSED', 4000, 5000, 'HELD')
			""", otherPass, guestId);
		trips.saveAndFlush(Trip.requestStart(guestId, otherPass, bike202, "EB-202", NOW.minusSeconds(29)));

		var found = trips.findByStatusAndRequestedAtLessThanEqual(TripStatus.STARTING, NOW.minus(UNLOCK_TIMEOUT));

		assertThat(found).extracting(Trip::getId).containsExactly(overdue.getId());
	}

}
