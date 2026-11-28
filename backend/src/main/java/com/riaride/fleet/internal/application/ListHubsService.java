package com.riaride.fleet.internal.application;

import static java.util.stream.Collectors.toMap;

import com.riaride.accounts.HotelDirectory;
import com.riaride.accounts.HotelSummary;
import com.riaride.fleet.internal.domain.Hub;
import com.riaride.fleet.internal.persistence.HubOccupancy;
import com.riaride.fleet.internal.persistence.HubRepository;
import com.riaride.fleet.internal.persistence.VehicleRepository;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Lists every hub of the network with its free slots (RIA-107), so a guest picks where to start or
// end a trip and staff see the network. Three queries whatever the number of hubs: the hubs, the
// docked bikes per hub and the hotels. The counts are read without locks: a trip that ends at the
// hub checks its free slots again under the hub's lock (VehicleDockingService).
@Service
public class ListHubsService {

  private final HubRepository hubs;
  private final VehicleRepository vehicles;
  private final HotelDirectory hotels;

  public ListHubsService(HubRepository hubs, VehicleRepository vehicles, HotelDirectory hotels) {
    this.hubs = hubs;
    this.vehicles = vehicles;
    this.hotels = hotels;
  }

  // By name, so the list does not depend on the database's order.
  @Transactional(readOnly = true)
  public List<ListedHub> list() {
    var docked =
        vehicles.countDockedByHub().stream()
            .collect(toMap(HubOccupancy::getHubId, HubOccupancy::getDocked));
    var hotelNames = hotels.findAll().stream().collect(toMap(HotelSummary::id, HotelSummary::name));
    return hubs.findAll().stream()
        .sorted(Comparator.comparing(Hub::getName))
        .map(hub ->
            new ListedHub(
                hub,
                hub.getHotelId() == null ? null : hotelNames.get(hub.getHotelId()),
                docked.getOrDefault(hub.getId(), 0L)))
        .toList();
  }
}
