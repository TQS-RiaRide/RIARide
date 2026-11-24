package com.riaride.fleet.internal.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.getxray.xray.junit.customjunitxml.annotations.Requirement;
import com.riaride.fleet.VehicleNotBookableException;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

// Unit test (Surefire): starting a trip reserves the bike (BR12 still applies), and the confirmed
// unlock puts it in use, off its hub (RIA-54).
class VehicleReservationTest {

  private static final BookingBatteryPolicy POLICY = new BookingBatteryPolicy(20);
  private static final UUID HUB = UUID.fromString("00000000-0000-0000-0000-00000000a001");
  private static final Instant READ_AT = Instant.parse("2026-11-30T10:00:00Z");

  private static Vehicle availableWithBattery(int percent) {
    var vehicle = Vehicle.register("EB-201", VehicleType.E_BIKE, HUB);
    vehicle.recordBattery(BatteryLevel.of(percent), READ_AT, READ_AT);
    return vehicle;
  }

  @Requirement("RIA-54")
  @Test
  void shouldBecomeReservedWhenBookable() {
    var vehicle = availableWithBattery(80);

    vehicle.reserve(POLICY);

    assertThat(vehicle.getStatus()).isEqualTo(VehicleStatus.RESERVED);
    assertThat(vehicle.getHubId()).isEqualTo(HUB);
  }

  @Requirement("RIA-54")
  @Test
  void shouldRefuseReservationWhenBatteryBelow20Percent() {
    var vehicle = availableWithBattery(19);

    assertThatThrownBy(() -> vehicle.reserve(POLICY))
        .isInstanceOf(VehicleNotBookableException.class);
    assertThat(vehicle.getStatus()).isEqualTo(VehicleStatus.AVAILABLE);
  }

  @Requirement("RIA-54")
  @ParameterizedTest
  @EnumSource(value = VehicleStatus.class, names = "AVAILABLE", mode = EnumSource.Mode.EXCLUDE)
  void shouldRefuseReservationWhenNotAvailable(VehicleStatus status) {
    var vehicle = Vehicle.withStatus("EB-201", VehicleType.E_BIKE, HUB, status);
    vehicle.recordBattery(BatteryLevel.of(100), READ_AT, READ_AT);

    assertThatThrownBy(() -> vehicle.reserve(POLICY))
        .isInstanceOf(VehicleNotBookableException.class);
    assertThat(vehicle.getStatus()).isEqualTo(status);
  }

  // A ridden vehicle has no hub, so it no longer takes a slot (BR10).
  @Requirement("RIA-54")
  @Test
  void shouldBeInUseAndOffItsHubWhenTheRideStarts() {
    var vehicle = availableWithBattery(80);
    vehicle.reserve(POLICY);

    vehicle.startRide();

    assertThat(vehicle.getStatus()).isEqualTo(VehicleStatus.IN_USE);
    assertThat(vehicle.getHubId()).isNull();
  }

  @Requirement("RIA-54")
  @ParameterizedTest
  @EnumSource(value = VehicleStatus.class, names = "RESERVED", mode = EnumSource.Mode.EXCLUDE)
  void shouldRefuseToStartARideUnlessReserved(VehicleStatus status) {
    var vehicle = Vehicle.withStatus("EB-201", VehicleType.E_BIKE, HUB, status);

    assertThatThrownBy(vehicle::startRide).isInstanceOf(IllegalStateException.class);
    assertThat(vehicle.getStatus()).isEqualTo(status);
  }
}
