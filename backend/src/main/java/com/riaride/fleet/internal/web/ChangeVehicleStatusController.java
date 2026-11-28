package com.riaride.fleet.internal.web;

import com.riaride.fleet.internal.application.ChangeVehicleStatusService;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

// An operator activates, deactivates or retires a bike (RIA-91): PUT /api/v1/vehicles/{id}/status
// with the status to reach. Asking for the current status again changes nothing, as PUT allows.
// SecurityConfiguration also restricts it to these roles, so another role gets 403 before the body
// is read.
@RestController
class ChangeVehicleStatusController {

  private final ChangeVehicleStatusService changeVehicleStatus;

  ChangeVehicleStatusController(ChangeVehicleStatusService changeVehicleStatus) {
    this.changeVehicleStatus = changeVehicleStatus;
  }

  @PutMapping("/api/v1/vehicles/{id}/status")
  @PreAuthorize("hasAnyRole('OPERATOR', 'ADMIN')")
  VehicleResponse change(@PathVariable UUID id, @RequestBody ChangeVehicleStatusRequest request) {
    return VehicleResponse.from(changeVehicleStatus.change(id, request.status()));
  }
}
