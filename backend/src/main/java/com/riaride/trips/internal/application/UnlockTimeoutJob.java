package com.riaride.trips.internal.application;

import com.riaride.trips.internal.domain.TripStatus;
import com.riaride.trips.internal.persistence.TripRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionOperations;

// Fails the trips whose unlock telemetry did not confirm within the timeout (RIA-54, BR13); a trip
// still starting exactly at the timeout has failed. Each trip is failed in a transaction of its own,
// under its row lock, and checked again there, so a trip confirmed in the meantime is left alone and
// one trip that cannot be failed does not stop the others. A failed trip locks the bike again; the
// reservation, the bike and the pass are not changed: the bike stays reserved until the booking
// expires, and the pass stays inactive. Public, so the acceptance tests can run it on their clock.
@Component
public class UnlockTimeoutJob {

	private static final Logger log = LoggerFactory.getLogger(UnlockTimeoutJob.class);

	private final TripRepository trips;
	private final ApplicationEventPublisher events;
	private final Clock clock;
	private final TripsProperties properties;
	private final TransactionOperations transactions;

	UnlockTimeoutJob(TripRepository trips, ApplicationEventPublisher events, Clock clock, TripsProperties properties,
		TransactionOperations transactions) {
		this.trips = trips;
		this.events = events;
		this.clock = clock;
		this.properties = properties;
		this.transactions = transactions;
	}

	// Returns how many trips it failed.
	@Scheduled(fixedDelayString = "${riaride.trips.unlock-timeout-check-interval}")
	public int failOverdueStarts() {
		var now = clock.instant();
		var timeout = properties.unlockTimeout();
		var failed = 0;
		for (var tripId : trips.findIdsByStatusAndRequestedAtLessThanEqual(TripStatus.STARTING, now.minus(timeout))) {
			try {
				if (Boolean.TRUE.equals(transactions.execute(status -> fail(tripId, now, timeout)))) {
					failed++;
				}
			} catch (RuntimeException notFailed) {
				log.error("Overdue trip {} not failed ({}); retrying on the next run", tripId,
					notFailed.getClass().getSimpleName());
			}
		}
		return failed;
	}

	private boolean fail(UUID tripId, Instant now, Duration timeout) {
		var trip = trips.findByIdForUpdate(tripId);
		if (trip.isEmpty() || !trip.get().failIfUnlockOverdue(now, timeout)) {
			return false;
		}
		events.publishEvent(new TripFailed(trip.get().getVehicleCode(), trip.get().getHubId()));
		return true;
	}

}
