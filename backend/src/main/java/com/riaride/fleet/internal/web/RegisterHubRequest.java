package com.riaride.fleet.internal.web;

import com.riaride.fleet.internal.application.RegisterHubCommand;
import com.riaride.fleet.internal.domain.Hub;
import com.riaride.fleet.internal.domain.HubKind;
import com.riaride.fleet.internal.domain.Names;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

// The hub registration form. The rules on name, kind, hotel, position and capacity are the
// domain's (Hub.register); here only the numbers' presence and the hotel name's shape are checked,
// since the domain takes plain numbers and the hotel name is looked up in the database.
record RegisterHubRequest(
    String name,
    HubKind kind,
    // The same rule as the hub name: no control or format characters (Unicode categories Cc and
    // Cf). PostgreSQL rejects NUL, and no invisible character belongs in a name.
    @Size(max = Hub.MAX_NAME_LENGTH, message = "No hotel of the group has a name this long.")
        @Pattern(
            regexp = NO_INVISIBLE_CHARACTERS,
            message = "A hotel name must not contain control or format characters.")
        String hotel,
    @NotNull(message = "The latitude is required.") Double latitude,
    @NotNull(message = "The longitude is required.") Double longitude,
    @NotNull(message = "The capacity is required.") Integer capacity) {

  static final String NO_INVISIBLE_CHARACTERS = "^[^\\p{Cc}\\p{Cf}]*$";

  // A blank hotel name counts as no hotel, so a hotel hub is asked to name its hotel and a
  // tourist spot hub is registered without one.
  RegisterHubCommand toCommand() {
    var strippedHotel = hotel == null ? "" : Names.stripSpaces(hotel);
    return new RegisterHubCommand(
        name, kind, strippedHotel.isEmpty() ? null : strippedHotel, latitude, longitude, capacity);
  }
}
