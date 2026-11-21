package com.riaride.accounts.internal.application;

import com.riaride.accounts.ActiveGuest;
import com.riaride.accounts.GuestDirectory;
import com.riaride.accounts.internal.persistence.GuestRepository;
import com.riaride.accounts.internal.persistence.StayRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class JpaGuestDirectory implements GuestDirectory {

	private final GuestRepository guests;
	private final StayRepository stays;
	private final Clock clock;

	JpaGuestDirectory(GuestRepository guests, StayRepository stays, Clock clock) {
		this.guests = guests;
		this.stays = stays;
		this.clock = clock;
	}

	@Override
	@Transactional(readOnly = true)
	public Optional<ActiveGuest> findActiveGuest(String keycloakId) {
		var today = LocalDate.now(clock);
		return guests.findByKeycloakId(keycloakId)
			.flatMap(guest -> stays.findById(guest.getStayId())
				.filter(stay -> !stay.hasEndedOn(today))
				.map(stay -> new ActiveGuest(guest.getId(), stay.getId(), stay.getCheckOut())));
	}

}
