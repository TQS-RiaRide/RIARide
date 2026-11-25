package com.riaride.maintenance.internal.application;

import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Maintenance settings under {@code riaride.maintenance}, validated at startup.
 *
 * @param sla how long a ticket has to be resolved, counted from its opening (RIA-92: 24 hours)
 */
@ConfigurationProperties("riaride.maintenance")
@Validated
public record MaintenanceProperties(@NotNull Duration sla) {}
