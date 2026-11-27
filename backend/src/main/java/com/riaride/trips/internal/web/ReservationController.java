package com.riaride.trips.internal.web;

import com.riaride.trips.internal.application.BookBikeCommand;
import com.riaride.trips.internal.application.BookBikeService;
import com.riaride.trips.internal.application.ReservationQueryService;
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

// Booking a bike in advance (RIA-90). The answer is 201 Created with the booking, held until
// expiresAt; starting a trip on the same bike before then uses it (POST /api/v1/trips).
@RestController
@RequestMapping("/api/v1/reservations")
class ReservationController {

  private final BookBikeService bookBike;
  private final ReservationQueryService reservations;

  ReservationController(BookBikeService bookBike, ReservationQueryService reservations) {
    this.bookBike = bookBike;
    this.reservations = reservations;
  }

  @PostMapping
  @PreAuthorize("hasRole('GUEST')")
  ResponseEntity<ReservationResponse> book(
      @Valid @RequestBody BookBikeRequest request, JwtAuthenticationToken authentication) {
    var reservation =
        bookBike.book(new BookBikeCommand(authentication.getName(), request.vehicleCode()));
    // fromCurrentRequestUri leaves out the query string of the request.
    var location =
        ServletUriComponentsBuilder.fromCurrentRequestUri()
            .path("/{id}")
            .buildAndExpand(reservation.getId())
            .toUri();
    return ResponseEntity.created(location).body(ReservationResponse.from(reservation));
  }

  @GetMapping("/{id}")
  @PreAuthorize("hasRole('GUEST')")
  ReservationResponse read(@PathVariable UUID id, JwtAuthenticationToken authentication) {
    return reservations
        .findForGuest(authentication.getName(), id)
        .map(ReservationResponse::from)
        .orElseThrow(ReservationNotFoundException::new);
  }
}
