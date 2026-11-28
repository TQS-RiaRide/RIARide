package com.riaride.fleet.internal.web;

import com.riaride.fleet.internal.application.ListHubsService;
import com.riaride.fleet.internal.application.RegisterHubService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

// Hub registration (RIA-56) and the hub list (RIA-107). SecurityConfiguration also restricts
// /api/v1/hubs to these roles, so another role gets 403 before the body is read, even when it is
// invalid.
@RestController
@RequestMapping("/api/v1/hubs")
class HubController {

  private final RegisterHubService registerHub;
  private final ListHubsService listHubs;

  HubController(RegisterHubService registerHub, ListHubsService listHubs) {
    this.registerHub = registerHub;
    this.listHubs = listHubs;
  }

  // Every platform role: guests choose where to ride, staff see the network.
  @GetMapping
  @PreAuthorize("hasAnyRole('GUEST', 'OPERATOR', 'TECHNICIAN', 'HOTEL_MANAGER', 'ADMIN')")
  List<HubResponse> list() {
    return listHubs.list().stream().map(HubResponse::from).toList();
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
