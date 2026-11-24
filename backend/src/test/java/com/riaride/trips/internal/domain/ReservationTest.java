package com.riaride.trips.internal.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.getxray.xray.junit.customjunitxml.annotations.Requirement;
import com.riaride.FixedClockConfiguration;
import com.riaride.fleet.ReservedVehicle;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

// Unit test (Surefire): starting a trip holds the bike for the pass (BR11: 15 minutes). A failed
// start leaves the reservation active, so the guest can try the same bike again; the trip that
// starts uses it. Expiring it is story 3.3 (RIA-54).
class ReservationTest {

  private static final Instant NOW = FixedClockConfiguration.NOW;
  private static final Duration HOLD = Duration.ofMinutes(15);
  private static final UUID PASS = UUID.fromString("0ca7add9-0d29-380f-80b5-f8bed8397bea");
  private static final UUID VEHICLE = UUID.fromString("00000000-0000-0000-0000-0000000e0201");
  private static final UUID HUB = UUID.fromString("00000000-0000-0000-0000-00000000a001");
  private static final ReservedVehicle EB_201 = new ReservedVehicle(VEHICLE, "EB-201", HUB);

  @Requirement("RIA-54")
  @Test
  void shouldHoldBikeFor15Minutes() {
    var reservation = Reservation.hold(PASS, EB_201, NOW, HOLD);

    assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.ACTIVE);
    assertThat(reservation.getExpiresAt()).isEqualTo(Instant.parse("2026-11-30T10:15:00Z"));
    assertThat(reservation.getPassId()).isEqualTo(PASS);
    assertThat(reservation.getVehicleId()).isEqualTo(VEHICLE);
    assertThat(reservation.getVehicleCode()).isEqualTo("EB-201");
    assertThat(reservation.getHubId()).isEqualTo(HUB);
  }

  @Requirement("RIA-54")
  @Test
  void shouldTellWhichBikeItHolds() {
    var reservation = Reservation.hold(PASS, EB_201, NOW, HOLD);

    assertThat(reservation.isFor("EB-201")).isTrue();
    assertThat(reservation.isFor("EB-202")).isFalse();
  }

  @Requirement("RIA-54")
  @Test
  void shouldBeUsedOnceTripStarts() {
    var reservation = Reservation.hold(PASS, EB_201, NOW, HOLD);

    reservation.markUsed();

    assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.USED);
  }

  @Requirement("RIA-54")
  @Test
  void shouldRefuseToBeUsedTwice() {
    var reservation = Reservation.hold(PASS, EB_201, NOW, HOLD);
    reservation.markUsed();

    assertThatThrownBy(reservation::markUsed).isInstanceOf(IllegalStateException.class);
    assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.USED);
  }
}
