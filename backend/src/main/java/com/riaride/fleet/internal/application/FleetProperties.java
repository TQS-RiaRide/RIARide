package com.riaride.fleet.internal.application;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Fleet settings under {@code riaride.fleet}, validated at startup.
 *
 * @param minBookingBatteryPct minimum battery, in percent, for a vehicle to be bookable (BR12);
 *     required, so a missing property fails startup instead of binding to 0
 */
@ConfigurationProperties("riaride.fleet")
@Validated
public record FleetProperties(@NotNull @Min(0) @Max(100) Integer minBookingBatteryPct) {}
