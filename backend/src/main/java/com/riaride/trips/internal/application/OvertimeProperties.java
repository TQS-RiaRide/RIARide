package com.riaride.trips.internal.application;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Overtime settings under {@code riaride.trips.overtime}, validated at startup (RIA-88). The
 * maximum trip duration is a rule of use the backoffice sets (RIA-96, {@code UsageRules}), so it is
 * not here.
 *
 * @param grace how long after the limit is not charged
 * @param pricePerBlockCents the price of each started 15-minute block
 * @param retryFirstDelay how long the first retry of a charge that failed for now waits; each
 *     further retry waits twice as long
 * @param retryMaxAttempts how many times a charge is tried before it is left to the deposit
 */
@ConfigurationProperties("riaride.trips.overtime")
@Validated
public record OvertimeProperties(
    @NotNull Duration grace,
    @Positive long pricePerBlockCents,
    @NotNull Duration retryFirstDelay,
    @Positive int retryMaxAttempts) {}
