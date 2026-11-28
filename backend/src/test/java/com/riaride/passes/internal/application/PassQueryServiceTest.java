package com.riaride.passes.internal.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import app.getxray.xray.junit.customjunitxml.annotations.Requirement;
import com.riaride.accounts.GuestDirectory;
import com.riaride.passes.internal.domain.Pass;
import com.riaride.passes.internal.domain.PassPrice;
import com.riaride.passes.internal.domain.PassStatus;
import com.riaride.passes.internal.domain.PassType;
import com.riaride.passes.internal.persistence.PassRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

// Unit test (Surefire): the guest's current pass for the dashboard (RIA-107) is their one open pass
// (pending, paid and unused, or active); a guest with none, or a user who is not a guest, has no
// current pass.
@ExtendWith(MockitoExtension.class)
class PassQueryServiceTest {

  private static final String KEYCLOAK_ID = "8c1f6a52-4d3e-4b9a-9f0e-1a2b3c4d5e6f";
  private static final UUID GUEST = UUID.fromString("5b7f0c2e-0d1a-4a7e-9a55-3c2d1e0f4a11");

  @Mock private GuestDirectory guests;

  @Mock private PassRepository passes;

  @InjectMocks private PassQueryService service;

  @Requirement("RIA-107")
  @Test
  void shouldGiveTheGuestsOpenPass() {
    var pass =
        Pass.purchase(
            GUEST,
            new PassPrice(PassType.STAY, 2500, 5000),
            GUEST + ":purchase-1",
            Instant.parse("2026-12-03T00:00:00Z"));
    when(guests.findGuestId(KEYCLOAK_ID)).thenReturn(Optional.of(GUEST));
    when(passes.findFirstByGuestIdAndStatusIn(GUEST, PassStatus.OPEN))
        .thenReturn(Optional.of(pass));

    assertThat(service.currentPass(KEYCLOAK_ID)).containsSame(pass);
  }

  @Requirement("RIA-107")
  @Test
  void shouldGiveNothingWhenTheGuestHoldsNoOpenPass() {
    when(guests.findGuestId(KEYCLOAK_ID)).thenReturn(Optional.of(GUEST));
    when(passes.findFirstByGuestIdAndStatusIn(GUEST, PassStatus.OPEN)).thenReturn(Optional.empty());

    assertThat(service.currentPass(KEYCLOAK_ID)).isEmpty();
  }

  @Requirement("RIA-107")
  @Test
  void shouldGiveNothingToAUserWhoIsNotAGuest() {
    when(guests.findGuestId(KEYCLOAK_ID)).thenReturn(Optional.empty());

    assertThat(service.currentPass(KEYCLOAK_ID)).isEmpty();
    verifyNoInteractions(passes);
  }
}
