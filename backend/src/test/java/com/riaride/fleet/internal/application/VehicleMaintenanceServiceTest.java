package com.riaride.fleet.internal.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import app.getxray.xray.junit.customjunitxml.annotations.Requirement;
import com.riaride.fleet.FaultHandling;
import com.riaride.fleet.FaultyVehicle;
import com.riaride.fleet.VehicleNotFoundException;
import com.riaride.fleet.VehicleRetiredException;
import com.riaride.fleet.internal.domain.Vehicle;
import com.riaride.fleet.internal.domain.VehicleStatus;
import com.riaride.fleet.internal.domain.VehicleType;
import com.riaride.fleet.internal.persistence.VehicleRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

// Unit test (Surefire): the fleet API that the maintenance module uses when a ticket is opened for
// a faulty bike (RIA-92). The bike is read with a lock, so a guest cannot book it at the same time;
// the rules per lifecycle state are in VehicleFaultTest.
@ExtendWith(MockitoExtension.class)
class VehicleMaintenanceServiceTest {

  private static final UUID HUB = UUID.fromString("00000000-0000-0000-0000-00000000a001");

  @Mock private VehicleRepository vehicles;

  private VehicleMaintenanceService service;

  @BeforeEach
  void setUp() {
    service = new VehicleMaintenanceService(vehicles);
  }

  @Requirement("RIA-92")
  @Test
  void shouldTakeTheLockedBikeOutOfServiceByCode() {
    var bike = Vehicle.register("EB-901", VehicleType.E_BIKE, HUB);
    when(vehicles.findByCodeForUpdate("eb-901")).thenReturn(Optional.of(bike));

    var faulty = service.reportFault("eb-901");

    assertThat(faulty)
        .isEqualTo(new FaultyVehicle(bike.getId(), "EB-901", FaultHandling.OUT_OF_SERVICE));
    assertThat(bike.getStatus()).isEqualTo(VehicleStatus.MAINTENANCE);
  }

  @Requirement("RIA-92")
  @Test
  void shouldThrowNotFoundForUnknownCode() {
    when(vehicles.findByCodeForUpdate("EB-999")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.reportFault("EB-999"))
        .isInstanceOf(VehicleNotFoundException.class);
  }

  // The maintenance module only sees the fleet's public types.
  @Requirement("RIA-92")
  @Test
  void shouldRefuseARetiredBikeWithAPublicException() {
    var bike = Vehicle.register("EB-901", VehicleType.E_BIKE, HUB);
    bike.changeStatusAsOperator(VehicleStatus.RETIRED);
    when(vehicles.findByCodeForUpdate("EB-901")).thenReturn(Optional.of(bike));

    assertThatThrownBy(() -> service.reportFault("EB-901"))
        .isInstanceOf(VehicleRetiredException.class);
    assertThat(bike.getStatus()).isEqualTo(VehicleStatus.RETIRED);
  }
}
