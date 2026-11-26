package com.riaride.trips.internal.web;

import com.riaride.trips.internal.application.EndTripCommand;
import com.riaride.trips.internal.application.EndTripService;
import com.riaride.trips.internal.application.StartTripCommand;
import com.riaride.trips.internal.application.StartTripService;
import com.riaride.trips.internal.application.TripQueryService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

// Starting a trip now (RIA-54). The answer is 202 Accepted: the trip is STARTING until telemetry
// confirms the unlock, so the client follows it at the Location it is given. Ending it at a hub
// (RIA-55) answers 200 with the ended trip, or 409 with the nearest hubs that have a free slot.
@RestController
@RequestMapping("/api/v1/trips")
class TripController {

  private final StartTripService startTrip;
  private final EndTripService endTrip;
  private final TripQueryService trips;

  TripController(StartTripService startTrip, EndTripService endTrip, TripQueryService trips) {
    this.startTrip = startTrip;
    this.endTrip = endTrip;
    this.trips = trips;
  }

  @PostMapping
  @PreAuthorize("hasRole('GUEST')")
  ResponseEntity<TripResponse> start(
      @Valid @RequestBody StartTripRequest request, JwtAuthenticationToken authentication) {
    var trip =
        startTrip.start(new StartTripCommand(authentication.getName(), request.vehicleCode()));
    // fromCurrentRequestUri leaves out the query string of the request.
    var location =
        ServletUriComponentsBuilder.fromCurrentRequestUri()
            .path("/{id}")
            .buildAndExpand(trip.getId())
            .toUri();
    return ResponseEntity.accepted().location(location).body(TripResponse.from(trip));
  }

  @PostMapping("/{id}/end")
  @PreAuthorize("hasRole('GUEST')")
  TripResponse end(
      @PathVariable UUID id,
      @Valid @RequestBody EndTripRequest request,
      JwtAuthenticationToken authentication) {
    return endTrip
        .end(new EndTripCommand(authentication.getName(), id, request.hubId()))
        .map(TripResponse::from)
        .orElseThrow(TripNotFoundException::new);
  }

  @GetMapping("/{id}")
  @PreAuthorize("hasRole('GUEST')")
  TripResponse read(@PathVariable UUID id, JwtAuthenticationToken authentication) {
    return trips
        .findForGuest(authentication.getName(), id)
        .map(TripResponse::from)
        .orElseThrow(TripNotFoundException::new);
  }
}
