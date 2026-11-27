package com.riaride.fleet.internal.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.getxray.xray.junit.customjunitxml.annotations.Requirement;
import com.riaride.fleet.FaultHandling;
import java.util.UUID;
import org.junit.jupiter.api.Test;

// Unit test (Surefire): a fault reported on a bike takes it out of service (RIA-92; Product
// Specification, section 3.3). A bike in use cannot be moved to maintenance until its trip ends:
// it is flagged, and the trip end moves it to maintenance instead of available.
class VehicleFaultTest {

  private static final UUID HUB = UUID.fromString("00000000-0000-0000-0000-00000000a001");
  private static final UUID END_HUB = UUID.fromString("00000000-0000-0000-0000-00000000a002");

  @Requirement("RIA-92")
  @Test
  void shouldMoveAnAvailableBikeToMaintenanceAtItsHub() {
    var bike = bike(VehicleStatus.AVAILABLE, HUB);

    var handling = bike.reportFault();

    assertThat(handling).isEqualTo(FaultHandling.OUT_OF_SERVICE);
    assertThat(bike.getStatus()).isEqualTo(VehicleStatus.MAINTENANCE);
    assertThat(bike.getHubId()).isEqualTo(HUB);
    assertThat(bike.isMaintenancePending()).isFalse();
  }

  // The fleet module cannot cancel the reservation itself (trips owns it), so it tells the caller.
  @Requirement("RIA-92")
  @Test
  void shouldMoveAReservedBikeToMaintenanceAndAskToCancelItsReservation() {
    var bike = bike(VehicleStatus.RESERVED, HUB);

    var handling = bike.reportFault();

    assertThat(handling).isEqualTo(FaultHandling.OUT_OF_SERVICE_RESERVATION_CANCELLED);
    assertThat(bike.getStatus()).isEqualTo(VehicleStatus.MAINTENANCE);
    assertThat(bike.getHubId()).isEqualTo(HUB);
  }

  @Requirement("RIA-92")
  @Test
  void shouldKeepABikeInUseInUseUntilItsTripEnds() {
    var bike = bike(VehicleStatus.IN_USE, null);

    var handling = bike.reportFault();

    assertThat(handling).isEqualTo(FaultHandling.PENDING_UNTIL_TRIP_ENDS);
    assertThat(bike.getStatus()).isEqualTo(VehicleStatus.IN_USE);
    assertThat(bike.isMaintenancePending()).isTrue();
  }

  @Requirement("RIA-92")
  @Test
  void shouldMoveAFlaggedBikeToMaintenanceWhenItsTripEnds() {
    var bike = bike(VehicleStatus.IN_USE, null);
    bike.reportFault();

    bike.endRide(END_HUB);

    assertThat(bike.getStatus()).isEqualTo(VehicleStatus.MAINTENANCE);
    assertThat(bike.getHubId()).isEqualTo(END_HUB);
    assertThat(bike.isMaintenancePending()).isFalse();
  }

  @Requirement("RIA-92")
  @Test
  void shouldKeepABikeInUseFlaggedWhenASecondFaultIsReported() {
    var bike = bike(VehicleStatus.IN_USE, null);
    bike.reportFault();

    var handling = bike.reportFault();

    assertThat(handling).isEqualTo(FaultHandling.PENDING_UNTIL_TRIP_ENDS);
    assertThat(bike.getStatus()).isEqualTo(VehicleStatus.IN_USE);
    assertThat(bike.isMaintenancePending()).isTrue();
  }

  // A bike the operator already deactivated can still get a ticket for its fault.
  @Requirement("RIA-92")
  @Test
  void shouldLeaveABikeInMaintenanceAsItIs() {
    var bike = bike(VehicleStatus.MAINTENANCE, HUB);

    var handling = bike.reportFault();

    assertThat(handling).isEqualTo(FaultHandling.ALREADY_OUT_OF_SERVICE);
    assertThat(bike.getStatus()).isEqualTo(VehicleStatus.MAINTENANCE);
    assertThat(bike.getHubId()).isEqualTo(HUB);
  }

  @Requirement("RIA-92")
  @Test
  void shouldRefuseAFaultOnARetiredBike() {
    var bike = bike(VehicleStatus.RETIRED, null);

    assertThatThrownBy(bike::reportFault)
        .isInstanceOf(VehicleStatusChangeRefusedException.class)
        .hasMessage("A bike that is retired cannot be changed to maintenance.");
    assertThat(bike.getStatus()).isEqualTo(VehicleStatus.RETIRED);
    assertThat(bike.isMaintenancePending()).isFalse();
  }

  // Regression for RIA-55: without a fault, the trip end still makes the bike available.
  @Requirement("RIA-92")
  @Test
  void shouldMakeABikeWithoutAFaultAvailableWhenItsTripEnds() {
    var bike = bike(VehicleStatus.IN_USE, null);

    bike.endRide(END_HUB);

    assertThat(bike.getStatus()).isEqualTo(VehicleStatus.AVAILABLE);
    assertThat(bike.isMaintenancePending()).isFalse();
  }

  private static Vehicle bike(VehicleStatus status, UUID hubId) {
    return Vehicle.withStatus("EB-901", VehicleType.E_BIKE, hubId, status);
  }
}
