package com.riaride.accounts.internal.web;

import com.riaride.accounts.internal.application.RegisterStayService;
import com.riaride.accounts.internal.application.StayRegistrar;
import jakarta.validation.Valid;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/v1/stays")
class StayController {

  private static final Logger log = LoggerFactory.getLogger(StayController.class);

  private static final String ROLE_ADMIN = "ROLE_ADMIN";
  private static final String HOTEL_ID_CLAIM = "hotel_id";

  private final RegisterStayService registerStay;

  StayController(RegisterStayService registerStay) {
    this.registerStay = registerStay;
  }

  @PostMapping
  @PreAuthorize("hasAnyRole('HOTEL_MANAGER', 'ADMIN')")
  ResponseEntity<StayResponse> register(
      @Valid @RequestBody RegisterStayRequest request, JwtAuthenticationToken authentication) {
    var stay = registerStay.register(request.toCommand(), registrar(authentication));
    var location =
        ServletUriComponentsBuilder.fromCurrentRequest()
            .path("/{id}")
            .buildAndExpand(stay.getId())
            .toUri();
    return ResponseEntity.created(location).body(StayResponse.from(stay));
  }

  private static StayRegistrar registrar(JwtAuthenticationToken authentication) {
    var isAdmin =
        authentication.getAuthorities().stream()
            .anyMatch(authority -> ROLE_ADMIN.equals(authority.getAuthority()));
    if (isAdmin) {
      return StayRegistrar.admin();
    }
    return StayRegistrar.managerOf(managedHotel(authentication.getToken()));
  }

  // A manager without a valid hotel_id in the token manages no hotel.
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
