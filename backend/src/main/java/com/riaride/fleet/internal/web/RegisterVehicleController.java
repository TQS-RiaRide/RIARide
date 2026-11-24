package com.riaride.fleet.internal.web;

import com.riaride.fleet.internal.application.RegisterVehicleService;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

// Vehicle registration at a hub (RIA-57): POST /api/v1/hubs/{hubId}/vehicles, next to the listing
// of the same collection (HubVehiclesController). SecurityConfiguration also restricts it to these
// roles, so another role gets 403 before the body is read.
@RestController
@RequestMapping("/api/v1/hubs/{hubId}/vehicles")
class RegisterVehicleController {

  private final RegisterVehicleService registerVehicle;

  RegisterVehicleController(RegisterVehicleService registerVehicle) {
    this.registerVehicle = registerVehicle;
  }

  @PostMapping
  @PreAuthorize("hasAnyRole('OPERATOR', 'ADMIN')")
  ResponseEntity<VehicleResponse> register(
      @PathVariable UUID hubId, @RequestBody RegisterVehicleRequest request) {
    var vehicle = registerVehicle.register(request.toCommand(hubId));
    // The vehicle stays a member of the fleet when it leaves this hub, so its own resource is
    // /api/v1/vehicles/{id}, not under the hub.
    var location =
        ServletUriComponentsBuilder.fromCurrentContextPath()
            .path("/api/v1/vehicles/{id}")
            .buildAndExpand(vehicle.getId())
            .toUri();
    return ResponseEntity.created(location).body(VehicleResponse.from(vehicle));
  }
}
