package com.riaride.fleet.internal.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import app.getxray.xray.junit.customjunitxml.annotations.Requirement;
import com.riaride.fleet.internal.domain.Hub;
import com.riaride.fleet.internal.domain.HubKind;
import com.riaride.fleet.internal.domain.InvalidHubException;
import com.riaride.fleet.internal.persistence.HubRepository;
import com.riaride.fleet.internal.persistence.VehicleRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

// Unit test (Surefire): a hotel manager or an admin changes how many bikes a hub takes (RIA-96,
// BR10). A manager changes the hubs of their hotel and the tourist spots; an admin, any hub. The
// bikes already docked must still fit: equal is allowed and leaves the hub full. The hub is read
// with its row lock (findByIdForUpdate), as when a trip ends there (VehicleDockingService).
@ExtendWith(MockitoExtension.class)
class ChangeHubCapacityServiceTest {

  private static final UUID MARES_PRAIA = UUID.fromString("00000000-0000-0000-0000-000000000002");
  private static final UUID MARES_CENTRO = UUID.fromString("00000000-0000-0000-0000-000000000001");

  @Mock private HubRepository hubs;

  @Mock private VehicleRepository vehicles;

  @InjectMocks private ChangeHubCapacityService service;

  private static Hub hotelHub(UUID hotelId) {
    return Hub.register("Marés Praia", HubKind.HOTEL, hotelId, 40.6405, -8.7538, 10);
  }

  private static Hub touristSpot() {
    return Hub.register("Miradouro da Ria", HubKind.TOURIST_SPOT, null, 40.64, -8.65, 10);
  }

  private Hub givenHub(Hub hub, long docked) {
    when(hubs.findByIdForUpdate(hub.getId())).thenReturn(Optional.of(hub));
    when(vehicles.countByHubId(hub.getId())).thenReturn(docked);
    return hub;
  }

  // The acceptance criterion: a hub with 8 bikes whose capacity changes to 8 is full.
  @Requirement("RIA-96")
  @Test
  void shouldLetTheManagerOfTheHotelSetTheCapacityToTheBikesDocked() {
    var hub = givenHub(hotelHub(MARES_PRAIA), 8);

    var changed = service.changeCapacity(hub.getId(), 8, HubEditor.managerOf(MARES_PRAIA));

    assertThat(changed.hub().getCapacity()).isEqualTo(8);
    assertThat(changed.dockedBikes()).isEqualTo(8);
  }

  @Requirement("RIA-96")
  @Test
  void shouldRefuseACapacityBelowTheBikesDocked() {
    var hub = givenHub(hotelHub(MARES_PRAIA), 8);

    assertThatThrownBy(() -> service.changeCapacity(hub.getId(), 7, HubEditor.admin()))
        .isInstanceOfSatisfying(
            CapacityBelowDockedBikesException.class,
            refused -> assertThat(refused.getDockedBikes()).isEqualTo(8));
    assertThat(hub.getCapacity()).isEqualTo(10);
  }

  @Requirement("RIA-96")
  @Test
  void shouldRefuseTheManagerOfAnotherHotel() {
    var hub = hotelHub(MARES_PRAIA);
    when(hubs.findByIdForUpdate(hub.getId())).thenReturn(Optional.of(hub));

    assertThatThrownBy(
            () -> service.changeCapacity(hub.getId(), 12, HubEditor.managerOf(MARES_CENTRO)))
        .isInstanceOf(HubAccessDeniedException.class);
    assertThat(hub.getCapacity()).isEqualTo(10);
    verifyNoInteractions(vehicles);
  }

  // A manager whose account has no hotel manages no hotel hub.
  @Requirement("RIA-96")
  @Test
  void shouldRefuseAManagerWithoutAHotelForAHotelHub() {
    var hub = hotelHub(MARES_PRAIA);
    when(hubs.findByIdForUpdate(hub.getId())).thenReturn(Optional.of(hub));

    assertThatThrownBy(() -> service.changeCapacity(hub.getId(), 12, HubEditor.managerOf(null)))
        .isInstanceOf(HubAccessDeniedException.class);
  }

  // Tourist spots belong to no hotel of the group, so any of its managers sets them.
  @Requirement("RIA-96")
  @Test
  void shouldLetAnyHotelManagerChangeATouristSpot() {
    var hub = givenHub(touristSpot(), 2);

    var changed = service.changeCapacity(hub.getId(), 4, HubEditor.managerOf(MARES_CENTRO));

    assertThat(changed.hub().getCapacity()).isEqualTo(4);
  }

  @Requirement("RIA-96")
  @Test
  void shouldLetAnAdminChangeAnyHub() {
    var hub = givenHub(hotelHub(MARES_PRAIA), 3);

    var changed = service.changeCapacity(hub.getId(), 20, HubEditor.admin());

    assertThat(changed.hub().getCapacity()).isEqualTo(20);
  }

  @Requirement("RIA-96")
  @Test
  void shouldRefuseAnUnknownHub() {
    var unknown = UUID.randomUUID();
    when(hubs.findByIdForUpdate(unknown)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.changeCapacity(unknown, 8, HubEditor.admin()))
        .isInstanceOf(HubNotFoundException.class);
  }

  // An invalid value is refused before the hub is read, so it takes no lock.
  @Requirement("RIA-96")
  @Test
  void shouldRefuseACapacityWithNoRoomBeforeReadingTheHub() {
    var anyHub = UUID.randomUUID();
    var admin = HubEditor.admin();

    assertThatThrownBy(() -> service.changeCapacity(anyHub, 0, admin))
        .isInstanceOf(InvalidHubException.class);
    verifyNoInteractions(hubs, vehicles);
  }
}
