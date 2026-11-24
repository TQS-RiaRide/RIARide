package com.riaride.fleet.internal.web;

import com.riaride.fleet.internal.application.RegisterHubService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

// Hub registration (RIA-56). SecurityConfiguration also restricts POST /api/v1/hubs to these
// roles, so another role gets 403 before the body is read, even when it is invalid.
@RestController
@RequestMapping("/api/v1/hubs")
class HubController {

  private final RegisterHubService registerHub;

  HubController(RegisterHubService registerHub) {
    this.registerHub = registerHub;
  }

  @PostMapping
  @PreAuthorize("hasAnyRole('OPERATOR', 'ADMIN')")
  ResponseEntity<HubResponse> register(@Valid @RequestBody RegisterHubRequest request) {
    var registered = registerHub.register(request.toCommand());
    // fromCurrentRequestUri leaves out the query string of the request.
    var location =
        ServletUriComponentsBuilder.fromCurrentRequestUri()
            .path("/{id}")
            .buildAndExpand(registered.hub().getId())
            .toUri();
    return ResponseEntity.created(location).body(HubResponse.from(registered));
  }
}
