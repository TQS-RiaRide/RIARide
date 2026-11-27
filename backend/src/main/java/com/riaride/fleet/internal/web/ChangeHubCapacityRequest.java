package com.riaride.fleet.internal.web;

import jakarta.validation.constraints.NotNull;

// The number of bikes the hub takes from now on (RIA-96). Its smallest value is checked by the
// domain (Hub.requireValidCapacity), so the API and the domain give the same reason.
record ChangeHubCapacityRequest(@NotNull(message = "The capacity is required.") Integer capacity) {}
