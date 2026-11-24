package com.riaride.trips.internal.web;

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
// confirms the unlock, so the client follows it at the Location it is given.
@RestController
@RequestMapping("/api/v1/trips")
class TripController {

  private final StartTripService startTrip;
  private final TripQueryService trips;

  TripController(StartTripService startTrip, TripQueryService trips) {
    this.startTrip = startTrip;
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

  @GetMapping("/{id}")
  @PreAuthorize("hasRole('GUEST')")
  TripResponse read(@PathVariable UUID id, JwtAuthenticationToken authentication) {
    return trips
        .findForGuest(authentication.getName(), id)
        .map(TripResponse::from)
        .orElseThrow(TripNotFoundException::new);
  }
}
