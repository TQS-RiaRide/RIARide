package com.riaride.trips.internal.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// The bike to start a trip on, by the code printed on it (for example EB-201).
record StartTripRequest(@NotBlank @Size(max = 32) String vehicleCode) {
}
