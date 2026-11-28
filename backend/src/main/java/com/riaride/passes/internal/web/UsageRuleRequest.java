package com.riaride.passes.internal.web;

import jakarta.validation.constraints.NotNull;

// Its limits are checked by the domain (UsageRule), so the API and the domain give the same reason.
record UsageRuleRequest(
    @NotNull(message = "The maximum trip duration is required.") Integer maxTripDurationMinutes) {}
