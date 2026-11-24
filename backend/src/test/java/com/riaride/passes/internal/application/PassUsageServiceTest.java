package com.riaride.passes.internal.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import app.getxray.xray.junit.customjunitxml.annotations.Requirement;
import com.riaride.passes.UsablePass;
import com.riaride.passes.internal.domain.Pass;
import com.riaride.passes.internal.domain.PassPrice;
import com.riaride.passes.internal.domain.PassStatus;
import com.riaride.passes.internal.domain.PassType;
import com.riaride.passes.internal.persistence.PassRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

// Unit test (Surefire): the passes API that the trips module uses (RIA-54). A guest's open pass can
// be used for a trip when it is paid and not active yet (BR4), or active and not past its end. The
// first trip activates it. A guest has at most one open pass (uq_pass_open_per_guest).
@ExtendWith(MockitoExtension.class)
class PassUsageServiceTest {

  private static final Instant NOW = Instant.parse("2026-11-30T10:00:00Z");
  private static final UUID GUEST = UUID.fromString("5b7f0c2e-0d1a-4a7e-9a55-3c2d1e0f4a11");
  private static final PassPrice STAY_PRICE = new PassPrice(PassType.STAY, 2500, 5000);

  @Mock private PassRepository passes;

  private PassUsageService service;

  @BeforeEach
  void setUp() {
    service = new PassUsageService(passes, Clock.fixed(NOW, ZoneOffset.UTC));
  }

  private static Pass pendingPass() {
    return Pass.purchase(GUEST, STAY_PRICE, GUEST + ":purchase-1");
  }

  private static Pass paidPass() {
    var pass = pendingPass();
    pass.confirmPayment();
    return pass;
  }

  private void givenOpenPass(Pass pass) {
    when(passes.findFirstByGuestIdAndStatusIn(GUEST, PassStatus.OPEN))
        .thenReturn(Optional.of(pass));
  }

  @Requirement("RIA-54")
  @Test
  void shouldFindInactivePassAsUsable() {
    var pass = paidPass();
    givenOpenPass(pass);

    assertThat(service.findUsablePass(GUEST)).contains(new UsablePass(pass.getId()));
  }

  @Requirement("RIA-54")
  @Test
  void shouldNotFindPendingPassAsUsable() {
    givenOpenPass(pendingPass());

    assertThat(service.findUsablePass(GUEST)).isEmpty();
  }

  @Requirement("RIA-54")
  @Test
  void shouldNotFindAnyPassForAGuestWithoutAnOpenPass() {
    when(passes.findFirstByGuestIdAndStatusIn(GUEST, PassStatus.OPEN)).thenReturn(Optional.empty());

    assertThat(service.findUsablePass(GUEST)).isEmpty();
  }

  // Setting expiresAt per pass type is the pass-expiry story, so the test sets it.
  @Requirement("RIA-54")
  @Test
  void shouldNotFindActivePassThatExpired() {
    var pass = paidPass();
    pass.activate(NOW.minusSeconds(3600));
    ReflectionTestUtils.setField(pass, "expiresAt", NOW.minusSeconds(1));
    givenOpenPass(pass);

    assertThat(service.findUsablePass(GUEST)).isEmpty();
  }

  // A pass whose end is now has ended.
  @Requirement("RIA-54")
  @Test
  void shouldNotFindActivePassThatExpiresNow() {
    var pass = paidPass();
    pass.activate(NOW.minusSeconds(3600));
    ReflectionTestUtils.setField(pass, "expiresAt", NOW);
    givenOpenPass(pass);

    assertThat(service.findUsablePass(GUEST)).isEmpty();
  }

  @Requirement("RIA-54")
  @Test
  void shouldFindActivePassThatHasNotExpired() {
    var pass = paidPass();
    pass.activate(NOW.minusSeconds(3600));
    ReflectionTestUtils.setField(pass, "expiresAt", NOW.plusSeconds(1));
    givenOpenPass(pass);

    assertThat(service.findUsablePass(GUEST)).contains(new UsablePass(pass.getId()));
  }

  @Requirement("RIA-54")
  @Test
  void shouldFindActivePassWithNoEndYetAsUsable() {
    var pass = paidPass();
    pass.activate(NOW.minusSeconds(3600));
    givenOpenPass(pass);

    assertThat(service.findUsablePass(GUEST)).contains(new UsablePass(pass.getId()));
  }

  @Requirement("RIA-54")
  @Test
  void shouldActivatePassOnFirstTrip() {
    var pass = paidPass();
    when(passes.findById(pass.getId())).thenReturn(Optional.of(pass));

    service.activateOnFirstTrip(pass.getId(), NOW);

    assertThat(pass.getStatus()).isEqualTo(PassStatus.ACTIVE);
    assertThat(pass.getActivatedAt()).isEqualTo(NOW);
  }

  @Requirement("RIA-54")
  @Test
  void shouldRefuseToActivateAnUnknownPass() {
    var unknown = UUID.randomUUID();
    when(passes.findById(unknown)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.activateOnFirstTrip(unknown, NOW))
        .isInstanceOf(IllegalStateException.class);
  }

  // Checked again when telemetry confirms the unlock, with the pass locked until the trip starts.
  @Requirement("RIA-54")
  @Test
  void shouldAcceptAPaidPassForATripThatStarts() {
    var pass = paidPass();
    when(passes.findByIdForUpdate(pass.getId())).thenReturn(Optional.of(pass));

    assertThat(service.canStartTrip(pass.getId(), NOW)).isTrue();
  }

  // The pass was refunded or cancelled while the unlock was pending.
  @Requirement("RIA-54")
  @Test
  void shouldNotAcceptAPassThatIsNoLongerUsable() {
    var pass = pendingPass();
    when(passes.findByIdForUpdate(pass.getId())).thenReturn(Optional.of(pass));

    assertThat(service.canStartTrip(pass.getId(), NOW)).isFalse();
  }

  @Requirement("RIA-54")
  @Test
  void shouldNotAcceptAnUnknownPassForATrip() {
    var unknown = UUID.randomUUID();
    when(passes.findByIdForUpdate(unknown)).thenReturn(Optional.empty());

    assertThat(service.canStartTrip(unknown, NOW)).isFalse();
  }
}
