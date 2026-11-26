package com.riaride.fleet.internal.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.getxray.xray.junit.customjunitxml.annotations.Requirement;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

// Unit test (Surefire): a vehicle in use that ends its ride is docked at the hub and available
// again (RIA-55). Whether the hub has a free slot is checked by VehicleDockingService.
class VehicleEndRideTest {

  private static final UUID START_HUB = UUID.fromString("00000000-0000-0000-0000-00000000a001");
  private static final UUID END_HUB = UUID.fromString("00000000-0000-0000-0000-00000000a002");

  @Requirement("RIA-55")
  @Test
  void shouldDockAVehicleInUseAtTheHubAndMakeItAvailable() {
    var bike = Vehicle.withStatus("EB-301", VehicleType.E_BIKE, null, VehicleStatus.IN_USE);

    bike.endRide(END_HUB);

    assertThat(bike.getStatus()).isEqualTo(VehicleStatus.AVAILABLE);
    assertThat(bike.getHubId()).isEqualTo(END_HUB);
  }

  @Requirement("RIA-55")
  @ParameterizedTest(name = "{0}")
  @EnumSource(value = VehicleStatus.class, names = "IN_USE", mode = EnumSource.Mode.EXCLUDE)
  void shouldOnlyEndTheRideOfAVehicleInUse(VehicleStatus status) {
    var bike = Vehicle.withStatus("EB-301", VehicleType.E_BIKE, START_HUB, status);

    assertThatThrownBy(() -> bike.endRide(END_HUB)).isInstanceOf(IllegalStateException.class);
    assertThat(bike.getStatus()).isEqualTo(status);
    assertThat(bike.getHubId()).isEqualTo(START_HUB);
  }

  @Requirement("RIA-55")
  @Test
  void shouldNeedAHub() {
    var bike = Vehicle.withStatus("EB-301", VehicleType.E_BIKE, null, VehicleStatus.IN_USE);

    assertThatThrownBy(() -> bike.endRide(null)).isInstanceOf(IllegalArgumentException.class);
    assertThat(bike.getStatus()).isEqualTo(VehicleStatus.IN_USE);
  }
}
