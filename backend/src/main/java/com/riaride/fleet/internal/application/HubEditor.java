package com.riaride.fleet.internal.application;

import com.riaride.fleet.internal.domain.Hub;
import com.riaride.fleet.internal.domain.HubKind;
import java.util.Objects;
import java.util.UUID;

// Who changes a hub's capacity (RIA-96): an admin changes any hub; a hotel manager changes the hubs
// of the hotel they manage and the tourist spots, which belong to no hotel of the group.
public final class HubEditor {

  private final boolean admin;
  private final UUID managedHotelId;

  private HubEditor(boolean admin, UUID managedHotelId) {
    this.admin = admin;
    this.managedHotelId = managedHotelId;
  }

  public static HubEditor admin() {
    return new HubEditor(true, null);
  }

  // managedHotelId may be null when the manager's account has no hotel; they can then change only
  // the tourist spots.
  public static HubEditor managerOf(UUID managedHotelId) {
    return new HubEditor(false, managedHotelId);
  }

  boolean canEdit(Hub hub) {
    return admin
        || hub.getKind() == HubKind.TOURIST_SPOT
        || managedHotelId != null && Objects.equals(managedHotelId, hub.getHotelId());
  }
}
