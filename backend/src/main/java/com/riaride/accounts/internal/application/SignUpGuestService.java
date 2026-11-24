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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

// Signs a guest up with the stay code from check-in (ADR 0010): checks the stay, creates the
// Keycloak user, then stores the guest. If storing fails, the Keycloak user is deleted.
// Not transactional on purpose: the Keycloak call must not run inside a database transaction,
// and saveAndFlush commits the insert, so a constraint violation surfaces here.
@Service
public class SignUpGuestService {

  private static final Logger log = LoggerFactory.getLogger(SignUpGuestService.class);

  private final StayRepository stays;
  private final GuestRepository guests;
  private final IdentityProvider identity;
  private final Clock clock;

  public SignUpGuestService(
      StayRepository stays, GuestRepository guests, IdentityProvider identity, Clock clock) {
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
    // A one-word name is used as both names: Keycloak requires both, and the form asks for neither.
    var lastName = names.length > 1 ? names[1] : names[0];
    var user =
        identity.createGuestUser(new NewGuestUser(email, command.password(), names[0], lastName));
    try {
      return guests.saveAndFlush(Guest.signUp(user.id(), stay.getId(), email, command.locale()));
    } catch (RuntimeException failure) {
      compensate(user, failure);
      throw failure;
    }
  }

  // Deletes only a user this sign-up created and no stored guest uses: with two identical
  // sign-ups at once, the one that stored its guest keeps its user. A failed delete must not
  // hide the original error, so it is attached to it and logged.
  private void compensate(GuestUser user, RuntimeException failure) {
    if (!user.created() || guests.existsByKeycloakId(user.id())) {
      return;
    }
    try {
      identity.deleteUser(user.id());
    } catch (RuntimeException cleanup) {
      failure.addSuppressed(cleanup);
      log.warn(
          "Could not delete Keycloak user {} after a failed sign-up ({})",
          user.id(),
          cleanup.getClass().getSimpleName());
    }
  }

  // Malformed, unknown and already used codes fail the same way (InvalidStayCodeException).
  private Stay usableStay(String typedCode) {
    var stay =
        parse(typedCode).flatMap(stays::findByStayCode).orElseThrow(InvalidStayCodeException::new);
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
