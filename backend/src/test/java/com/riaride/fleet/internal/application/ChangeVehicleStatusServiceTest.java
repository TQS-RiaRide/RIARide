package com.riaride.fleet.internal.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import app.getxray.xray.junit.customjunitxml.annotations.Requirement;
import com.riaride.fleet.VehicleNotFoundException;
import com.riaride.fleet.internal.domain.Vehicle;
import com.riaride.fleet.internal.domain.VehicleStatus;
import com.riaride.fleet.internal.domain.VehicleStatusChangeRefusedException;
import com.riaride.fleet.internal.domain.VehicleType;
import com.riaride.fleet.internal.persistence.VehicleRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

// Unit test (Surefire): the operator changes a bike's status by its id (RIA-91). The bike is
// loaded with its row locked, so a booking cannot reserve it while it is being retired; the
// concurrent and persisted cases are in ChangeVehicleStatusApiIT.
@ExtendWith(MockitoExtension.class)
class ChangeVehicleStatusServiceTest {

  private static final UUID HUB = UUID.fromString("00000000-0000-0000-0000-00000000a091");

  @Mock private VehicleRepository vehicles;

  private ChangeVehicleStatusService service;

  @BeforeEach
  void setUp() {
    service = new ChangeVehicleStatusService(vehicles);
  }

  private Vehicle givenAvailableBike() {
    var vehicle = Vehicle.register("EB-701", VehicleType.E_BIKE, HUB);
    when(vehicles.findByIdForUpdate(vehicle.getId())).thenReturn(Optional.of(vehicle));
    return vehicle;
  }

  @Requirement("RIA-91")
  @Test
  void shouldRetireAnAvailableBike() {
    var vehicle = givenAvailableBike();

    var changed = service.change(vehicle.getId(), VehicleStatus.RETIRED);

    assertThat(changed.getStatus()).isEqualTo(VehicleStatus.RETIRED);
    assertThat(changed.getHubId()).isNull();
  }

  @Requirement("RIA-91")
  @Test
  void shouldRefuseAChangeOutsideTheOperatorTransitions() {
    var vehicle = givenAvailableBike();
    vehicle.changeStatusAsOperator(VehicleStatus.RETIRED);

    assertThatThrownBy(() -> service.change(vehicle.getId(), VehicleStatus.AVAILABLE))
        .isInstanceOf(VehicleStatusChangeRefusedException.class);
  }

  @Requirement("RIA-91")
  @Test
  void shouldRefuseAnUnknownBike() {
    var unknown = UUID.randomUUID();
    when(vehicles.findByIdForUpdate(unknown)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.change(unknown, VehicleStatus.RETIRED))
        .isInstanceOf(VehicleNotFoundException.class);
  }
}
