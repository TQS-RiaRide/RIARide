package com.riaride.accounts.internal.application;

import com.riaride.accounts.internal.domain.Guest;
import com.riaride.accounts.internal.domain.Stay;
import com.riaride.accounts.internal.domain.StayAlreadyEndedException;
import com.riaride.accounts.internal.domain.StayCode;
import com.riaride.accounts.internal.persistence.GuestRepository;
import com.riaride.accounts.internal.persistence.StayRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Optional;
import org.springframework.stereotype.Service;

// Signs a guest up with the stay code from check-in (ADR 0010): checks the stay, creates the
// Keycloak user, then stores the guest. If storing fails, the Keycloak user is deleted.
// Not transactional on purpose: the Keycloak call must not run inside a database transaction,
// and saveAndFlush commits the insert, so a constraint violation surfaces here.
@Service
public class SignUpGuestService {

	private final StayRepository stays;
	private final GuestRepository guests;
	private final IdentityProvider identity;
	private final Clock clock;

	public SignUpGuestService(StayRepository stays, GuestRepository guests, IdentityProvider identity, Clock clock) {
		this.stays = stays;
		this.guests = guests;
		this.identity = identity;
		this.clock = clock;
	}

	public Guest signUp(SignUpGuestCommand command) {
		var email = command.email().strip().toLowerCase(Locale.ROOT);
		var stay = usableStay(command.stayCode());
		if (guests.existsByEmail(email)) {
			throw new EmailAlreadyRegisteredException();
		}
		var names = stay.getGuestName().strip().split("\\s+", 2);
		var lastName = names.length > 1 ? names[1] : names[0];
		var keycloakId = identity.createGuestUser(new NewGuestUser(email, command.password(), names[0], lastName));
		try {
			return guests.saveAndFlush(Guest.signUp(keycloakId, stay.getId(), email, command.locale()));
		} catch (RuntimeException failure) {
			identity.deleteUser(keycloakId);
			throw failure;
		}
	}

	// Malformed, unknown and already used codes fail the same way (InvalidStayCodeException).
	private Stay usableStay(String typedCode) {
		var stay = parse(typedCode)
			.flatMap(stays::findByStayCode)
			.orElseThrow(InvalidStayCodeException::new);
		if (stay.hasEndedOn(LocalDate.now(clock))) {
			throw new StayAlreadyEndedException();
		}
		if (guests.existsByStayId(stay.getId())) {
			throw new InvalidStayCodeException();
		}
		return stay;
	}

	private static Optional<StayCode> parse(String typedCode) {
		try {
			return Optional.of(new StayCode(typedCode.strip().toUpperCase(Locale.ROOT)));
		} catch (IllegalArgumentException malformed) {
			return Optional.empty();
		}
	}

}
