package com.riaride.trips.internal.application;

import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Trip settings under {@code riaride.trips}, validated at startup.
 *
 * @param unlockTimeout how long telemetry has to confirm an unlock before the trip fails (BR13);
 *     a trip still starting exactly at the timeout has failed
 * @param reservationHold how long a bike stays reserved for the pass (BR11)
 */
@ConfigurationProperties("riaride.trips")
@Validated
public record TripsProperties(@NotNull Duration unlockTimeout, @NotNull Duration reservationHold) {
}
