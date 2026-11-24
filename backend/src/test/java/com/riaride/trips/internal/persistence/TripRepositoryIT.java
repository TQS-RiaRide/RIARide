package com.riaride.trips.internal.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.getxray.xray.junit.customjunitxml.annotations.Requirement;
import com.riaride.TestcontainersConfiguration;
import com.riaride.fleet.ReservedVehicle;
import com.riaride.trips.internal.domain.Reservation;
import com.riaride.trips.internal.domain.ReservationStatus;
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
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

// Integration test (Failsafe): the reservation and trip tables (Flyway V7) on PostgreSQL in
// Testcontainers. The database itself guarantees one trip in progress per pass (BR6) and one active
// reservation per bike and per pass, so concurrent starts cannot both succeed (RIA-54).
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
class TripRepositoryIT {

  private static final Instant NOW = Instant.parse("2026-11-30T10:00:00Z");
  private static final Duration UNLOCK_TIMEOUT = Duration.ofSeconds(30);
  private static final Duration HOLD = Duration.ofMinutes(15);
  private static final String MARES_CENTRO = "00000000-0000-0000-0000-000000000001";

  @Autowired private TripRepository trips;

  @Autowired private ReservationRepository reservations;

  @Autowired private JdbcTemplate jdbc;

  @Autowired private TestEntityManager entityManager;

  private UUID guestId;
  private UUID passId;
  private UUID hubId;
  private UUID bike201;
  private UUID bike202;

  @BeforeEach
  void guestWithAPassAndTwoBikes() {
    var stayId = UUID.randomUUID();
    jdbc.update(
        """
        insert into stay (id, hotel_id, guest_name, stay_code, check_in, check_out, status)
        values (?, ?::uuid, 'Rita Almeida', 'TRIP2345', date '2026-11-28', date '2026-12-02', 'ACTIVE')
        """,
        stayId,
        MARES_CENTRO);
    guestId = UUID.randomUUID();
    jdbc.update(
        """
        insert into guest (id, keycloak_id, stay_id, email, locale)
        values (?, ?, ?, 'rita.almeida@example.test', 'pt')
        """,
        guestId,
        UUID.randomUUID().toString(),
        stayId);
    passId = pass("INACTIVE");
    hubId = UUID.randomUUID();
    jdbc.update(
        """
        insert into hub (id, name, kind, hotel_id, latitude, longitude, capacity)
        values (?, 'Marés Centro', 'HOTEL', ?::uuid, 40.6405, -8.6538, 10)
        """,
        hubId,
        MARES_CENTRO);
    bike201 = bike("EB-201");
    bike202 = bike("EB-202");
  }

  // A guest holds one open pass at a time, so any other pass of theirs is a closed one.
  private UUID pass(String status) {
    var id = UUID.randomUUID();
    jdbc.update(
        """
        insert into pass (id, guest_id, pass_type, status, price_cents, deposit_cents, deposit_status)
        values (?, ?, 'STAY', ?, 4000, 5000, 'HELD')
        """,
        id,
        guestId,
        status);
    return id;
  }

  private UUID bike(String code) {
    var id = UUID.randomUUID();
    jdbc.update(
        """
        insert into vehicle (id, code, type, status, hub_id, battery_pct)
        values (?, ?, 'E_BIKE', 'RESERVED', ?, 80)
        """,
        id,
        code,
        hubId);
    return id;
  }

  private Reservation reservation(UUID pass, UUID vehicleId, String code, Instant at) {
    return reservations.saveAndFlush(
        Reservation.hold(pass, new ReservedVehicle(vehicleId, code, hubId), at, HOLD));
  }

  private Trip startingTrip(Reservation reservation, Instant requestedAt) {
    return trips.saveAndFlush(Trip.requestStart(reservation, guestId, requestedAt));
  }

  @Requirement("RIA-54")
  @Test
  void shouldStoreAStartingTripWithItsReservation() {
    var reservation = reservation(passId, bike201, "EB-201", NOW);
    var trip = startingTrip(reservation, NOW);
    // Read back from the database, not from the persistence context.
    entityManager.clear();

    assertThat(trips.findById(trip.getId()))
        .get()
        .satisfies(found -> {
          assertThat(found.getStatus()).isEqualTo(TripStatus.STARTING);
          assertThat(found.getPassId()).isEqualTo(passId);
          assertThat(found.getReservationId()).isEqualTo(reservation.getId());
          assertThat(found.getVehicleCode()).isEqualTo("EB-201");
          assertThat(found.getHubId()).isEqualTo(hubId);
          assertThat(found.getRequestedAt()).isEqualTo(NOW);
        });
    assertThat(reservations.findById(reservation.getId()))
        .get()
        .satisfies(found -> {
          assertThat(found.getStatus()).isEqualTo(ReservationStatus.ACTIVE);
          assertThat(found.getExpiresAt()).isEqualTo(NOW.plus(HOLD));
        });
  }

  @Requirement("RIA-54")
  @Test
  void shouldRefuseSecondActiveTripForSamePass() {
    var reservation = reservation(passId, bike201, "EB-201", NOW);
    startingTrip(reservation, NOW);

    assertThatThrownBy(() -> startingTrip(reservation, NOW.plusSeconds(1)))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("uq_trip_active_per_pass");
  }

  // A failed start leaves the reservation active, and the retry on the same bike reuses it.
  @Requirement("RIA-54")
  @Test
  void shouldAllowNewTripAfterFailedOne() {
    var reservation = reservation(passId, bike201, "EB-201", NOW);
    var failed = Trip.requestStart(reservation, guestId, NOW);
    failed.failIfUnlockOverdue(NOW.plus(UNLOCK_TIMEOUT), UNLOCK_TIMEOUT);
    trips.saveAndFlush(failed);

    startingTrip(reservation, NOW.plusSeconds(60));

    assertThat(trips.count()).isEqualTo(2);
  }

  @Requirement("RIA-54")
  @Test
  void shouldRefuseSecondActiveReservationForSameVehicle() {
    reservation(passId, bike201, "EB-201", NOW);
    var otherPass = pass("CLOSED");

    assertThatThrownBy(() -> reservation(otherPass, bike201, "EB-201", NOW.plusSeconds(1)))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("uq_reservation_active_per_vehicle");
  }

  @Requirement("RIA-54")
  @Test
  void shouldRefuseSecondActiveReservationForSamePass() {
    reservation(passId, bike201, "EB-201", NOW);

    assertThatThrownBy(() -> reservation(passId, bike202, "EB-202", NOW.plusSeconds(1)))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("uq_reservation_active_per_pass");
  }

  // Once the trip starts, the reservation is used, and the pass and the bike can be held again
  // later (after the trip ends, RIA-55).
  @Requirement("RIA-54")
  @Test
  void shouldAllowNewReservationOnceTheLastOneIsUsed() {
    var used = reservation(passId, bike201, "EB-201", NOW);
    used.markUsed();
    reservations.saveAndFlush(used);

    reservation(passId, bike201, "EB-201", NOW.plusSeconds(3600));

    assertThat(reservations.count()).isEqualTo(2);
  }

  @Requirement("RIA-54")
  @Test
  void shouldFindActiveReservationOfAPass() {
    var reservation = reservation(passId, bike201, "EB-201", NOW);

    assertThat(reservations.findByPassIdAndStatus(passId, ReservationStatus.ACTIVE))
        .get()
        .extracting(Reservation::getId)
        .isEqualTo(reservation.getId());
    assertThat(reservations.findByPassIdAndStatus(passId, ReservationStatus.USED)).isEmpty();
  }

  @Requirement("RIA-54")
  @Test
  void shouldTellWhetherAPassHasATripInProgress() {
    assertThat(trips.existsByPassIdAndStatusIn(passId, TripStatus.ACTIVE)).isFalse();

    startingTrip(reservation(passId, bike201, "EB-201", NOW), NOW);

    assertThat(trips.existsByPassIdAndStatusIn(passId, TripStatus.ACTIVE)).isTrue();
  }

  // Telemetry names the bike; the trip it confirms or fails is the bike's latest one.
  @Requirement("RIA-54")
  @Test
  void shouldFindTheLatestTripOfABike() {
    var reservation = reservation(passId, bike201, "EB-201", NOW);
    var failed = Trip.requestStart(reservation, guestId, NOW);
    failed.failIfUnlockOverdue(NOW.plus(UNLOCK_TIMEOUT), UNLOCK_TIMEOUT);
    trips.saveAndFlush(failed);
    var retry = startingTrip(reservation, NOW.plusSeconds(60));

    assertThat(trips.findFirstByVehicleCodeOrderByRequestedAtDesc("EB-201"))
        .get()
        .extracting(Trip::getId)
        .isEqualTo(retry.getId());
    assertThat(trips.findFirstByVehicleCodeOrderByRequestedAtDesc("EB-202")).isEmpty();
  }

  // The timeout job looks for trips still starting at or before the cutoff (now minus 30 s). Each
  // trip has a pass of its own, since a pass has one trip in progress at most.
  @Requirement("RIA-54")
  @Test
  void shouldFindOverdueStartingTripIds() {
    var overdue =
        startingTrip(
            reservation(passId, bike201, "EB-201", NOW.minusSeconds(31)), NOW.minusSeconds(31));
    var onTime =
        startingTrip(
            reservation(pass("CLOSED"), bike202, "EB-202", NOW.minusSeconds(29)),
            NOW.minusSeconds(29));

    var found =
        trips.findIdsByStatusAndRequestedAtLessThanEqual(
            TripStatus.STARTING, NOW.minus(UNLOCK_TIMEOUT));

    assertThat(found).containsExactly(overdue.getId()).doesNotContain(onTime.getId());
  }

  // Exactly 30 s after the request the unlock is already late (BR13).
  @Requirement("RIA-54")
  @Test
  void shouldFindTripRequestedExactlyAtTheCutoff() {
    var atCutoff =
        startingTrip(
            reservation(passId, bike201, "EB-201", NOW.minusSeconds(30)), NOW.minusSeconds(30));

    var found =
        trips.findIdsByStatusAndRequestedAtLessThanEqual(
            TripStatus.STARTING, NOW.minus(UNLOCK_TIMEOUT));

    assertThat(found).containsExactly(atCutoff.getId());
  }

  @Requirement("RIA-54")
  @Test
  void shouldFindATripOnlyForItsGuest() {
    var trip = startingTrip(reservation(passId, bike201, "EB-201", NOW), NOW);

    assertThat(trips.findByIdAndGuestId(trip.getId(), guestId)).isPresent();
    assertThat(trips.findByIdAndGuestId(trip.getId(), UUID.randomUUID())).isEmpty();
  }

  @Requirement("RIA-54")
  @Test
  void shouldFindATripByIdForUpdate() {
    var trip = startingTrip(reservation(passId, bike201, "EB-201", NOW), NOW);

    assertThat(trips.findByIdForUpdate(trip.getId()))
        .get()
        .extracting(Trip::getId)
        .isEqualTo(trip.getId());
  }
}
