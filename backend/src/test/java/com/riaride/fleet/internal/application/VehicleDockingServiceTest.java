package com.riaride.fleet.internal.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import app.getxray.xray.junit.customjunitxml.annotations.Requirement;
import com.riaride.fleet.HubWithoutFreeSlotException;
import com.riaride.fleet.NearbyHub;
import com.riaride.fleet.UnknownHubException;
import com.riaride.fleet.VehicleNotInUseException;
import com.riaride.fleet.internal.domain.BatteryLevel;
import com.riaride.fleet.internal.domain.BookingBatteryPolicy;
import com.riaride.fleet.internal.domain.Hub;
import com.riaride.fleet.internal.domain.HubKind;
import com.riaride.fleet.internal.domain.Vehicle;
import com.riaride.fleet.internal.domain.VehicleStatus;
import com.riaride.fleet.internal.domain.VehicleType;
import com.riaride.fleet.internal.persistence.HubOccupancy;
import com.riaride.fleet.internal.persistence.HubRepository;
import com.riaride.fleet.internal.persistence.VehicleRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

// Unit test (Surefire): the fleet API that docks the bike of a trip that ends (RIA-55, BR10). A hub
// with a free slot takes the bike; a full hub refuses it, leaves it in use and suggests at most
// three other hubs with a free slot, nearest first. That two ends at the same hub cannot both take
// its last slot is checked in EndTripApiIT.
@ExtendWith(MockitoExtension.class)
class VehicleDockingServiceTest {

  private static final UUID START_HUB = UUID.fromString("00000000-0000-0000-0000-00000000a001");
  private static final Instant READ_AT = Instant.parse("2026-11-30T10:00:00Z");

  @Mock private VehicleRepository vehicles;

  @Mock private HubRepository hubs;

  private VehicleDockingService service;
  private Vehicle bike;

  @BeforeEach
  void setUp() {
    service = new VehicleDockingService(vehicles, hubs);
    bike = Vehicle.register("EB-301", VehicleType.E_BIKE, START_HUB);
    bike.recordBattery(BatteryLevel.of(80), READ_AT, READ_AT);
    bike.reserve(new BookingBatteryPolicy(20));
    bike.startRide();
  }

  private static Hub hub(String name, double latitude, double longitude, int capacity) {
    return Hub.register(name, HubKind.TOURIST_SPOT, null, latitude, longitude, capacity);
  }

  private static HubOccupancy occupancy(Hub hub, long docked) {
    return new HubOccupancy() {
      @Override
      public UUID getHubId() {
        return hub.getId();
      }

      @Override
      public long getDocked() {
        return docked;
      }
    };
  }

  private void givenTheBikeAndHub(Hub hub, long docked) {
    when(vehicles.findByIdForUpdate(bike.getId())).thenReturn(Optional.of(bike));
    when(hubs.findByIdForUpdate(hub.getId())).thenReturn(Optional.of(hub));
    when(vehicles.countByHubId(hub.getId())).thenReturn(docked);
  }

  @Requirement("RIA-55")
  @Test
  void shouldDockTheBikeAtAHubWithAFreeSlot() {
    var praia = hub("Praia", 40.6405, -8.7538, 10);
    givenTheBikeAndHub(praia, 9);

    service.dock(bike.getId(), praia.getId());

    assertThat(bike.getStatus()).isEqualTo(VehicleStatus.AVAILABLE);
    assertThat(bike.getHubId()).isEqualTo(praia.getId());
  }

  // The example of the RIA-55 feature: of the other hubs, only those with a free slot are
  // suggested, nearest first, with their free slots and their distance from the full hub.
  @Requirement("RIA-55")
  @Test
  void shouldRefuseAFullHubAndSuggestTheNearestHubsWithAFreeSlot() {
    var praia = hub("Praia", 40.6405, -8.7538, 10);
    var salinas = hub("Salinas", 40.6510, -8.6820, 8);
    var centro = hub("Centro", 40.6443, -8.6455, 12);
    var fullNearby = hub("Praia Norte", 40.6420, -8.7500, 4);
    givenTheBikeAndHub(praia, 10);
    when(hubs.findAll()).thenReturn(List.of(centro, praia, fullNearby, salinas));
    when(vehicles.countDockedByHub())
        .thenReturn(
            List.of(
                occupancy(praia, 10),
                occupancy(fullNearby, 4),
                occupancy(salinas, 3),
                occupancy(centro, 6)));

    assertThatThrownBy(() -> service.dock(bike.getId(), praia.getId()))
        .isInstanceOfSatisfying(
            HubWithoutFreeSlotException.class,
            full ->
                assertThat(full.nearbyHubs())
                    .containsExactly(
                        new NearbyHub(salinas.getId(), "Salinas", 5, 6169),
                        new NearbyHub(centro.getId(), "Centro", 6, 9147)));
    assertThat(bike.getStatus()).isEqualTo(VehicleStatus.IN_USE);
    assertThat(bike.getHubId()).isNull();
  }

  // A hub with no vehicle docked is not in the occupancy list: all its slots are free.
  @Requirement("RIA-55")
  @Test
  void shouldSuggestAtMostThreeHubs() {
    var full = hub("Full", 40.0, -8.0, 1);
    var first = hub("First", 40.0, -8.01, 2);
    var second = hub("Second", 40.0, -8.02, 2);
    var third = hub("Third", 40.0, -8.03, 2);
    var fourth = hub("Fourth", 40.0, -8.04, 2);
    givenTheBikeAndHub(full, 1);
    when(hubs.findAll()).thenReturn(List.of(fourth, third, full, second, first));
    when(vehicles.countDockedByHub()).thenReturn(List.of(occupancy(full, 1)));

    assertThatThrownBy(() -> service.dock(bike.getId(), full.getId()))
        .isInstanceOfSatisfying(
            HubWithoutFreeSlotException.class,
            refused ->
                assertThat(refused.nearbyHubs())
                    .extracting(NearbyHub::name)
                    .containsExactly("First", "Second", "Third"));
  }

  @Requirement("RIA-55")
  @Test
  void shouldSuggestNoHubWhenNoOtherHubHasAFreeSlot() {
    var praia = hub("Praia", 40.6405, -8.7538, 1);
    var salinas = hub("Salinas", 40.6510, -8.6820, 1);
    givenTheBikeAndHub(praia, 1);
    when(hubs.findAll()).thenReturn(List.of(praia, salinas));
    when(vehicles.countDockedByHub())
        .thenReturn(List.of(occupancy(praia, 1), occupancy(salinas, 1)));

    assertThatThrownBy(() -> service.dock(bike.getId(), praia.getId()))
        .isInstanceOfSatisfying(
            HubWithoutFreeSlotException.class,
            refused -> assertThat(refused.nearbyHubs()).isEmpty());
  }

  // Two ends of the same trip at once: the second locks the bike after the first docked it.
  @Requirement("RIA-55")
  @Test
  void shouldRefuseABikeThatIsNoLongerInUseWithoutLookingAtTheHub() {
    var praia = hub("Praia", 40.6405, -8.7538, 10);
    bike.endRide(praia.getId());
    when(vehicles.findByIdForUpdate(bike.getId())).thenReturn(Optional.of(bike));

    assertThatThrownBy(() -> service.dock(bike.getId(), praia.getId()))
        .isInstanceOf(VehicleNotInUseException.class);
    assertThat(bike.getStatus()).isEqualTo(VehicleStatus.AVAILABLE);
    assertThat(bike.getHubId()).isEqualTo(praia.getId());
    verifyNoInteractions(hubs);
  }

  @Requirement("RIA-55")
  @Test
  void shouldRefuseAnUnknownHubAndLeaveTheBikeInUse() {
    var unknown = UUID.randomUUID();
    when(vehicles.findByIdForUpdate(bike.getId())).thenReturn(Optional.of(bike));
    when(hubs.findByIdForUpdate(unknown)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.dock(bike.getId(), unknown))
        .isInstanceOf(UnknownHubException.class);
    assertThat(bike.getStatus()).isEqualTo(VehicleStatus.IN_USE);
  }
}
