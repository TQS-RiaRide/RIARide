package com.riaride.passes.internal.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.getxray.xray.junit.customjunitxml.annotations.Requirement;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

// Unit test (Surefire): BR4, a pass is activated on the first trip, not at purchase (RIA-54).
class PassActivationTest {

	private static final UUID GUEST = UUID.fromString("5b7f0c2e-0d1a-4a7e-9a55-3c2d1e0f4a11");
	private static final PassPrice STAY_PRICE = new PassPrice(PassType.STAY, 2500, 5000);
	private static final Instant FIRST_TRIP = Instant.parse("2026-11-30T10:00:10Z");

	private static Pass paidPass() {
		var pass = Pass.purchase(GUEST, STAY_PRICE, GUEST + ":purchase-1");
		pass.confirmPayment();
		return pass;
	}

	@Requirement("RIA-54")
	@Test
	void shouldActivateAnInactivePassAtTheFirstTrip() {
		var pass = paidPass();

		pass.activate(FIRST_TRIP);

		assertThat(pass.getStatus()).isEqualTo(PassStatus.ACTIVE);
		assertThat(pass.getActivatedAt()).isEqualTo(FIRST_TRIP);
	}

	// Only the first trip activates the pass; later trips keep its activation time.
	@Requirement("RIA-54")
	@Test
	void shouldKeepTheFirstActivationTimeWhenAlreadyActive() {
		var pass = paidPass();
		pass.activate(FIRST_TRIP);

		pass.activate(FIRST_TRIP.plusSeconds(3600));

		assertThat(pass.getStatus()).isEqualTo(PassStatus.ACTIVE);
		assertThat(pass.getActivatedAt()).isEqualTo(FIRST_TRIP);
	}

	@Requirement("RIA-54")
	@Test
	void shouldRefuseToActivateAPassThatIsNotPaidYet() {
		var pending = Pass.purchase(GUEST, STAY_PRICE, GUEST + ":purchase-1");

		assertThatThrownBy(() -> pending.activate(FIRST_TRIP)).isInstanceOf(IllegalStateException.class);
		assertThat(pending.getStatus()).isEqualTo(PassStatus.PENDING);
		assertThat(pending.getActivatedAt()).isNull();
	}

	// No transition closes a pass yet (that is the pass-expiry story), so the test sets the status.
	@Requirement("RIA-54")
	@Test
	void shouldRefuseToActivateAClosedPass() {
		var closed = paidPass();
		ReflectionTestUtils.setField(closed, "status", PassStatus.CLOSED);

		assertThatThrownBy(() -> closed.activate(FIRST_TRIP)).isInstanceOf(IllegalStateException.class);
		assertThat(closed.getStatus()).isEqualTo(PassStatus.CLOSED);
		assertThat(closed.getActivatedAt()).isNull();
	}

}
