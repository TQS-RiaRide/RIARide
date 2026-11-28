package com.riaride.passes.internal.web;

import com.riaride.passes.internal.domain.Pass;
import com.riaride.passes.internal.domain.PassStatus;
import com.riaride.passes.internal.domain.PassType;
import com.riaride.payments.Money;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

// activatedAt and expiresAt are null until the first trip (BR4); stayEndsAt is when the stay ends,
// which no pass outlasts (RIA-87).
record PassResponse(
    UUID id,
    PassType passType,
    PassStatus status,
    int priceCents,
    int depositCents,
    String currency,
    Instant activatedAt,
    Instant expiresAt,
    Instant stayEndsAt) {

  static PassResponse from(Pass pass) {
    return new PassResponse(
        pass.getId(),
        pass.getPassType(),
        pass.getStatus(),
        pass.getPriceCents(),
        pass.getDepositCents(),
        Money.CURRENCY.toUpperCase(Locale.ROOT),
        pass.getActivatedAt(),
        pass.getExpiresAt(),
        pass.getStayEndsAt());
  }
}
