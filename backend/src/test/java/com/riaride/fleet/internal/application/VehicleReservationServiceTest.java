package com.riaride.fleet.internal.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import app.getxray.xray.junit.customjunitxml.annotations.Requirement;
import com.riaride.fleet.ReservedVehicle;
import com.riaride.fleet.VehicleNotFoundException;
import com.riaride.fleet.internal.domain.BatteryLevel;
import com.riaride.fleet.internal.domain.BookingBatteryPolicy;
import com.riaride.fleet.internal.domain.Vehicle;
import com.riaride.fleet.internal.domain.VehicleStatus;
import com.riaride.fleet.internal.domain.VehicleType;
import com.riaride.fleet.internal.persistence.VehicleRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

// Unit test (Surefire): the fleet API that the trips module uses to reserve a bike when a trip is
// requested, and to put it in use once telemetry confirms the unlock (RIA-54). The vehicle row is
// read with a lock, so two guests cannot reserve the same bike; that case is in StartTripApiIT.
@ExtendWith(MockitoExtension.class)
class VehicleReservationServiceTest {

  private static final UUID HUB = UUID.fromString("00000000-0000-0000-0000-00000000a001");
  private static final Instant READ_AT = Instant.parse("2026-11-30T10:00:00Z");

  @Mock private VehicleRepository vehicles;

  private VehicleReservationService service;

  @BeforeEach
  void setUp() {
    service = new VehicleReservationService(vehicles, new BookingBatteryPolicy(20));
  }

  private static Vehicle availableBike(String code, int batteryPct) {
    var vehicle = Vehicle.register(code, VehicleType.E_BIKE, HUB);
    vehicle.recordBattery(BatteryLevel.of(batteryPct), READ_AT, READ_AT);
    return vehicle;
  }

  @Requirement("RIA-54")
  @Test
  void shouldReserveBookableVehicleLockedByCode() {
    var bike = availableBike("EB-201", 80);
    when(vehicles.findByCodeForUpdate("EB-201")).thenReturn(Optional.of(bike));

    var reserved = service.reserve("EB-201");

    assertThat(reserved).isEqualTo(new ReservedVehicle(bike.getId(), "EB-201", HUB));
    assertThat(bike.getStatus()).isEqualTo(VehicleStatus.RESERVED);
  }

  @Requirement("RIA-54")
  @Test
  void shouldThrowNotFoundForUnknownCode() {
    when(vehicles.findByCodeForUpdate("EB-999")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.reserve("EB-999"))
        .isInstanceOf(VehicleNotFoundException.class);
  }

  @Requirement("RIA-54")
  @Test
  void shouldStartRideOfReservedVehicle() {
    var bike = availableBike("EB-201", 80);
    bike.reserve(new BookingBatteryPolicy(20));
    when(vehicles.findByIdForUpdate(bike.getId())).thenReturn(Optional.of(bike));

    service.startRide(bike.getId());

    assertThat(bike.getStatus()).isEqualTo(VehicleStatus.IN_USE);
    assertThat(bike.getHubId()).isNull();
  }
}
