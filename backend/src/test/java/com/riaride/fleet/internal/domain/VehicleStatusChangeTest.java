package com.riaride.fleet.internal.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.getxray.xray.junit.customjunitxml.annotations.Requirement;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullSource;

// Unit test (Surefire): an operator activates (maintenance -> available), deactivates (available ->
// maintenance) or retires (available or maintenance -> retired) a bike (RIA-91). Every other change
// is refused and leaves the bike as it was: bookings, trips and faults move a bike through the
// other transitions of the lifecycle, not the operator. Asking for the status the bike already has
// changes nothing. A retired bike leaves its hub, so guests no longer see it and its slot is free.
class VehicleStatusChangeTest {

  private static final UUID HUB = UUID.fromString("00000000-0000-0000-0000-00000000a091");

  private static Vehicle bikeWithStatus(VehicleStatus status) {
    return Vehicle.withStatus("EB-701", VehicleType.E_BIKE, HUB, status);
  }

  @Requirement("RIA-91")
  @ParameterizedTest(name = "{0} -> {1}: {2}")
  @CsvSource({
    "AVAILABLE,   AVAILABLE,   unchanged",
    "AVAILABLE,   MAINTENANCE, changed",
    "AVAILABLE,   RETIRED,     changed",
    "RESERVED,    AVAILABLE,   refused",
    "RESERVED,    MAINTENANCE, refused",
    "RESERVED,    RETIRED,     refused",
    "IN_USE,      AVAILABLE,   refused",
    "IN_USE,      MAINTENANCE, refused",
    "IN_USE,      RETIRED,     refused",
    "MAINTENANCE, AVAILABLE,   changed",
    "MAINTENANCE, MAINTENANCE, unchanged",
    "MAINTENANCE, RETIRED,     changed",
    "RETIRED,     AVAILABLE,   refused",
    "RETIRED,     MAINTENANCE, refused",
    "RETIRED,     RETIRED,     unchanged"
  })
  void shouldApplyOnlyTheOperatorTransitions(VehicleStatus from, VehicleStatus to, String outcome) {
    var vehicle = bikeWithStatus(from);

    if ("refused".equals(outcome)) {
      assertThatThrownBy(() -> vehicle.changeStatusAsOperator(to))
          .isInstanceOf(VehicleStatusChangeRefusedException.class);
      assertThat(vehicle.getStatus()).isEqualTo(from);
    } else {
      vehicle.changeStatusAsOperator(to);
      assertThat(vehicle.getStatus()).isEqualTo(to);
    }
  }

  @Requirement("RIA-91")
  @ParameterizedTest
  @EnumSource(
      value = VehicleStatus.class,
      names = {"AVAILABLE", "MAINTENANCE"})
  void shouldLeaveItsHubWhenRetired(VehicleStatus from) {
    var vehicle = bikeWithStatus(from);

    vehicle.changeStatusAsOperator(VehicleStatus.RETIRED);

    assertThat(vehicle.getStatus()).isEqualTo(VehicleStatus.RETIRED);
    assertThat(vehicle.getHubId()).isNull();
  }

  @Requirement("RIA-91")
  @Test
  void shouldStayDockedWhenDeactivated() {
    var vehicle = bikeWithStatus(VehicleStatus.AVAILABLE);

    vehicle.changeStatusAsOperator(VehicleStatus.MAINTENANCE);

    assertThat(vehicle.getHubId()).isEqualTo(HUB);
  }

  @Requirement("RIA-91")
  @Test
  void shouldStayDockedWhenActivated() {
    var vehicle = bikeWithStatus(VehicleStatus.MAINTENANCE);

    vehicle.changeStatusAsOperator(VehicleStatus.AVAILABLE);

    assertThat(vehicle.getHubId()).isEqualTo(HUB);
  }

  @Requirement("RIA-91")
  @Test
  void shouldRefuseToRetireABikeInUse() {
    var vehicle = bikeWithStatus(VehicleStatus.IN_USE);

    assertThatThrownBy(() -> vehicle.changeStatusAsOperator(VehicleStatus.RETIRED))
        .isInstanceOf(VehicleStatusChangeRefusedException.class)
        .hasMessage("A bike that is in use cannot be changed to retired.");
    assertThat(vehicle.getStatus()).isEqualTo(VehicleStatus.IN_USE);
  }

  // Reserved and in use are reached by booking and riding, never set by the operator.
  @Requirement("RIA-91")
  @ParameterizedTest
  @EnumSource(
      value = VehicleStatus.class,
      names = {"RESERVED", "IN_USE"})
  @NullSource
  void shouldRejectAStatusTheOperatorCannotSet(VehicleStatus target) {
    var vehicle = bikeWithStatus(VehicleStatus.AVAILABLE);

    assertThatThrownBy(() -> vehicle.changeStatusAsOperator(target))
        .isInstanceOf(InvalidVehicleException.class)
        .extracting(exception -> ((InvalidVehicleException) exception).getField())
        .isEqualTo("status");
    assertThat(vehicle.getStatus()).isEqualTo(VehicleStatus.AVAILABLE);
  }
}
