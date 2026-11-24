package com.riaride.fleet.internal.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import app.getxray.xray.junit.customjunitxml.annotations.Requirement;
import com.riaride.fleet.internal.domain.BatteryLevel;
import com.riaride.fleet.internal.domain.BookingBatteryPolicy;
import com.riaride.fleet.internal.domain.Vehicle;
import com.riaride.fleet.internal.domain.VehicleType;
import com.riaride.fleet.internal.persistence.HubRepository;
import com.riaride.fleet.internal.persistence.VehicleRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

// Unit test (Surefire): the vehicles at a hub, each marked bookable or not by BR12.
class ListHubVehiclesServiceTest {

  private static final UUID HUB = UUID.fromString("00000000-0000-0000-0000-00000000a001");
  private static final Instant READ_AT = Instant.parse("2026-11-30T10:00:00Z");

  private final HubRepository hubs = mock(HubRepository.class);
  private final VehicleRepository vehicles = mock(VehicleRepository.class);
  private final ListHubVehiclesService service =
      new ListHubVehiclesService(hubs, vehicles, new BookingBatteryPolicy(20));

  private static Vehicle withBattery(String code, int percent) {
    var vehicle = Vehicle.register(code, VehicleType.E_BIKE, HUB);
    vehicle.recordBattery(BatteryLevel.of(percent), READ_AT, READ_AT);
    return vehicle;
  }

  @Requirement("RIA-59")
  @Test
  void shouldMarkOnlyVehiclesWithEnoughBatteryAsBookable() {
    when(hubs.existsById(HUB)).thenReturn(true);
    when(vehicles.findByHubIdOrderByCode(HUB))
        .thenReturn(
            List.of(
                withBattery("EB-601", 18), withBattery("EB-602", 20), withBattery("EB-603", 90)));

    var listed = service.list(HUB);

    assertThat(listed)
        .extracting(HubVehicle::code, HubVehicle::batteryPct, HubVehicle::bookable)
        .containsExactly(
            tuple("EB-601", 18, false), tuple("EB-602", 20, true), tuple("EB-603", 90, true));
  }

  @Requirement("RIA-59")
  @Test
  void shouldRejectAnUnknownHub() {
    when(hubs.existsById(HUB)).thenReturn(false);

    assertThatThrownBy(() -> service.list(HUB)).isInstanceOf(HubNotFoundException.class);
  }
}
