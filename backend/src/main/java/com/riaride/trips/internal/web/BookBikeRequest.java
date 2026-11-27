package com.riaride.trips.internal.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// The bike to book, by the code printed on it (for example EB-701).
record BookBikeRequest(@NotBlank @Size(max = 32) String vehicleCode) {}
