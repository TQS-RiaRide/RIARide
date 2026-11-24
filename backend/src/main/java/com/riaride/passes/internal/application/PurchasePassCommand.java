package com.riaride.passes.internal.application;

import com.riaride.passes.internal.domain.PassType;

// keycloakId identifies the guest from their token; idempotencyKey identifies this purchase.
public record PurchasePassCommand(
    String keycloakId, PassType passType, String paymentMethod, String idempotencyKey) {}
