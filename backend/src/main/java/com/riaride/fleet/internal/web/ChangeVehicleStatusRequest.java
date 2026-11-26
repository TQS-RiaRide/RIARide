package com.riaride.fleet.internal.web;

import com.riaride.fleet.internal.domain.VehicleStatus;

// The status the operator asks for: AVAILABLE (activate), MAINTENANCE (deactivate) or RETIRED.
record ChangeVehicleStatusRequest(VehicleStatus status) {}
