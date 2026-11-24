package com.riaride.fleet.internal.web;

import com.riaride.fleet.internal.application.HubVehicle;
import com.riaride.fleet.internal.application.ListHubVehiclesService;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// The vehicles at a hub, each marked bookable by BR12 (RIA-59). Guests see it to choose a bike;
// operators and admins to follow the fleet.
@RestController
@RequestMapping("/api/v1/hubs/{hubId}/vehicles")
class HubVehiclesController {

  private final ListHubVehiclesService listHubVehicles;

  HubVehiclesController(ListHubVehiclesService listHubVehicles) {
    this.listHubVehicles = listHubVehicles;
  }

  @GetMapping
  @PreAuthorize("hasAnyRole('GUEST', 'OPERATOR', 'ADMIN')")
  List<HubVehicle> list(@PathVariable UUID hubId) {
    return listHubVehicles.list(hubId);
  }
}
