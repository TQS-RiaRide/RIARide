package com.riaride.passes.internal.application;

import com.riaride.accounts.GuestDirectory;
import com.riaride.passes.internal.domain.Pass;
import com.riaride.passes.internal.domain.PassStatus;
import com.riaride.passes.internal.persistence.PassRepository;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Reads the guest's current pass for their dashboard (RIA-107): the one open pass a guest may hold
// (Product Owner decision, RIA-52), whether its payment is in progress, it is paid and unused, or
// active. A closed pass is not current.
@Service
public class PassQueryService {

  private final GuestDirectory guests;
  private final PassRepository passes;

  public PassQueryService(GuestDirectory guests, PassRepository passes) {
    this.guests = guests;
    this.passes = passes;
  }

  // Empty when the user is not a guest or holds no open pass.
  @Transactional(readOnly = true)
  public Optional<Pass> currentPass(String keycloakId) {
    return guests
        .findGuestId(keycloakId)
        .flatMap(guestId -> passes.findFirstByGuestIdAndStatusIn(guestId, PassStatus.OPEN));
  }
}
