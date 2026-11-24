package com.riaride.accounts.internal.web;

import com.riaride.accounts.internal.application.SignUpGuestService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

// Guest sign-up (RIA-51). Public: the guest has no account, and so no token, yet.
@RestController
@RequestMapping("/api/v1/guests")
class GuestController {

  private final SignUpGuestService signUpGuest;

  GuestController(SignUpGuestService signUpGuest) {
    this.signUpGuest = signUpGuest;
  }

  @PostMapping
  ResponseEntity<GuestResponse> signUp(@Valid @RequestBody SignUpGuestRequest request) {
    var guest = signUpGuest.signUp(request.toCommand());
    var location =
        ServletUriComponentsBuilder.fromCurrentRequest()
            .path("/{id}")
            .buildAndExpand(guest.getId())
            .toUri();
    return ResponseEntity.created(location).body(GuestResponse.from(guest));
  }
}
