package com.riaride.fleet.internal.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.getxray.xray.junit.customjunitxml.annotations.Requirement;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

// Unit test (Surefire): the hotel group changes how many bikes a hub takes (RIA-96, BR10), with the
// same smallest value as at registration (RIA-56). Whether the docked bikes still fit is checked
// by ChangeHubCapacityService, which counts them under the hub's lock.
class HubCapacityTest {

  private static Hub hubWithRoomFor(int capacity) {
    return Hub.register("Miradouro da Ria", HubKind.TOURIST_SPOT, null, 40.64, -8.65, capacity);
  }

  @Requirement("RIA-96")
  @ParameterizedTest(name = "a hub for 10 bikes can take {0}")
  @ValueSource(ints = {1, 8, 10, 25})
  void shouldTakeTheNewCapacity(int capacity) {
    var hub = hubWithRoomFor(10);

    hub.changeCapacity(capacity);

    assertThat(hub.getCapacity()).isEqualTo(capacity);
  }

  @Requirement("RIA-96")
  @ParameterizedTest(name = "a capacity of {0} is refused")
  @ValueSource(ints = {0, -1, Integer.MIN_VALUE})
  void shouldRefuseACapacityWithNoRoomForABike(int capacity) {
    var hub = hubWithRoomFor(10);

    assertThatThrownBy(() -> hub.changeCapacity(capacity))
        .isInstanceOfSatisfying(
            InvalidHubException.class,
            invalid -> {
              assertThat(invalid.getField()).isEqualTo("capacity");
              assertThat(invalid.getMessage()).doesNotContain(String.valueOf(capacity));
            });
    assertThat(hub.getCapacity()).isEqualTo(10);
  }
}
