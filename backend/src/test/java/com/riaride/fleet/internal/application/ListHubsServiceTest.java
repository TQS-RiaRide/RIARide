package com.riaride.fleet.internal.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.when;

import app.getxray.xray.junit.customjunitxml.annotations.Requirement;
import com.riaride.accounts.HotelDirectory;
import com.riaride.accounts.HotelSummary;
import com.riaride.fleet.internal.domain.Hub;
import com.riaride.fleet.internal.domain.HubKind;
import com.riaride.fleet.internal.persistence.HubOccupancy;
import com.riaride.fleet.internal.persistence.HubRepository;
import com.riaride.fleet.internal.persistence.VehicleRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

// Unit test (Surefire): the hub list (RIA-107) names each hub's hotel, counts its docked bikes, and
// is ordered by name. A hub with no bike docked has all its slots free.
@ExtendWith(MockitoExtension.class)
class ListHubsServiceTest {

  private static final UUID MARES_PRAIA = UUID.fromString("00000000-0000-0000-0000-000000000002");

  @Mock private HubRepository hubs;

  @Mock private VehicleRepository vehicles;

  @Mock private HotelDirectory hotels;

  @InjectMocks private ListHubsService service;

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

  @Requirement("RIA-107")
  @Test
  void shouldListEveryHubByNameWithItsHotelAndDockedBikes() {
    var praia = Hub.register("Marés Praia", HubKind.HOTEL, MARES_PRAIA, 40.6405, -8.7538, 10);
    var miradouro = Hub.register("Miradouro da Ria", HubKind.TOURIST_SPOT, null, 40.64, -8.65, 6);
    when(hubs.findAll()).thenReturn(List.of(miradouro, praia));
    when(vehicles.countDockedByHub()).thenReturn(List.of(occupancy(praia, 9)));
    when(hotels.findAll()).thenReturn(List.of(new HotelSummary(MARES_PRAIA, "Marés Praia")));

    var listed = service.list();

    assertThat(listed)
        .extracting(hub -> hub.hub().getName(), ListedHub::hotelName, ListedHub::dockedBikes)
        .containsExactly(
            tuple("Marés Praia", "Marés Praia", 9L), tuple("Miradouro da Ria", null, 0L));
  }

  @Requirement("RIA-107")
  @Test
  void shouldListNothingWhenTheNetworkHasNoHub() {
    when(hubs.findAll()).thenReturn(List.of());
    when(vehicles.countDockedByHub()).thenReturn(List.of());
    when(hotels.findAll()).thenReturn(List.of());

    assertThat(service.list()).isEmpty();
  }
}
