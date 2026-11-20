package com.riaride.fleet.internal.application;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Fleet settings under {@code riaride.fleet}.
 *
 * @param minBookingBatteryPct minimum battery, in percent, for a vehicle to be bookable (BR12)
 */
@ConfigurationProperties("riaride.fleet")
public record FleetProperties(int minBookingBatteryPct) {
}
