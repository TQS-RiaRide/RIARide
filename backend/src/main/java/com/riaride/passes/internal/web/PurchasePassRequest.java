package com.riaride.passes.internal.web;

import com.riaride.passes.internal.domain.PassType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

// paymentMethod is the payment provider's payment method: in the sandbox, a Stripe test one
// such as pm_card_visa. Card numbers never reach the backend.
record PurchasePassRequest(
    @NotNull PassType passType,
    @NotBlank @Size(max = 255) @Pattern(regexp = "^[A-Za-z0-9_]+$") String paymentMethod) {}
