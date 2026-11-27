package com.riaride.maintenance.internal.application;

import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Maintenance settings under {@code riaride.maintenance}, validated at startup.
 *
 * @param sla how long a ticket has to be resolved, counted from its opening (RIA-92: 24 hours); it
 *     must be positive, or no ticket could satisfy sla_due_at > opened_at
 */
@ConfigurationProperties("riaride.maintenance")
@Validated
public record MaintenanceProperties(@NotNull @DurationMin(nanos = 1) Duration sla) {}
