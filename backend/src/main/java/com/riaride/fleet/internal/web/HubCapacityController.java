package com.riaride.fleet.internal.web;

import com.riaride.fleet.internal.application.ChangeHubCapacityService;
import com.riaride.fleet.internal.application.HubEditor;
import jakarta.validation.Valid;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// A rule of use (RIA-96): the hotel group sets how many bikes a hub takes. SecurityConfiguration
// also restricts PUT /api/v1/hubs/*/capacity to these roles, so another role gets 403 before the
// body is read.
@RestController
@RequestMapping("/api/v1/hubs/{hubId}/capacity")
class HubCapacityController {

  private static final Logger log = LoggerFactory.getLogger(HubCapacityController.class);

  private static final String ROLE_ADMIN = "ROLE_ADMIN";
  private static final String HOTEL_ID_CLAIM = "hotel_id";

  private final ChangeHubCapacityService changeCapacity;

  HubCapacityController(ChangeHubCapacityService changeCapacity) {
    this.changeCapacity = changeCapacity;
  }

  @PutMapping
  @PreAuthorize("hasAnyRole('HOTEL_MANAGER', 'ADMIN')")
  HubCapacityResponse change(
      @PathVariable UUID hubId,
      @Valid @RequestBody ChangeHubCapacityRequest request,
      JwtAuthenticationToken authentication) {
    return HubCapacityResponse.from(
        changeCapacity.changeCapacity(hubId, request.capacity(), editor(authentication)));
  }

  private static HubEditor editor(JwtAuthenticationToken authentication) {
    var isAdmin =
        authentication.getAuthorities().stream()
            .anyMatch(authority -> ROLE_ADMIN.equals(authority.getAuthority()));
    return isAdmin
        ? HubEditor.admin()
        : HubEditor.managerOf(managedHotel(authentication.getToken()));
  }

  // As for stays (StayController): a manager without a valid hotel_id in the token manages no
  // hotel.
  private static UUID managedHotel(Jwt token) {
    var hotelId = token.getClaimAsString(HOTEL_ID_CLAIM);
    if (hotelId == null) {
      return null;
    }
    try {
      return UUID.fromString(hotelId);
    } catch (IllegalArgumentException invalid) {
      // The claim value is not logged; a misconfigured Keycloak user is the likely cause.
      log.debug("Ignoring a hotel_id claim that is not a UUID");
      return null;
    }
  }
}
