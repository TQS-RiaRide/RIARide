package com.riaride.passes.internal.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.getxray.xray.junit.customjunitxml.annotations.Requirement;
import java.util.UUID;
import org.junit.jupiter.api.Test;

// Unit test (Surefire): a pass is bought at the base price of its type (BR3), with the deposit
// (BR5), and is not active until the first trip (BR4).
class PassTest {

	private static final UUID GUEST = UUID.fromString("5b7f0c2e-0d1a-4a7e-9a55-3c2d1e0f4a11");
	private static final PassPrice STAY_PRICE = new PassPrice(PassType.STAY, 2500, 5000);

	@Requirement("RIA-52")
	@Test
	void shouldCreatePassThatIsNotActiveYet() {
		var pass = Pass.purchase(GUEST, STAY_PRICE);

		assertThat(pass.getStatus()).isEqualTo(PassStatus.INACTIVE);
		assertThat(pass.getActivatedAt()).isNull();
		assertThat(pass.getExpiresAt()).isNull();
	}

	@Requirement("RIA-52")
	@Test
	void shouldCopyPriceAndDepositOfItsType() {
		var pass = Pass.purchase(GUEST, STAY_PRICE);

		assertThat(pass.getGuestId()).isEqualTo(GUEST);
		assertThat(pass.getPassType()).isEqualTo(PassType.STAY);
		assertThat(pass.getPriceCents()).isEqualTo(2500);
		assertThat(pass.getDepositCents()).isEqualTo(5000);
		assertThat(pass.getDepositStatus()).isEqualTo(DepositStatus.HELD);
	}

	@Requirement("RIA-52")
	@Test
	void shouldChargePriceAndDepositTogether() {
		var pass = Pass.purchase(GUEST, STAY_PRICE);

		assertThat(pass.amountToCharge().cents()).isEqualTo(7500);
	}

	// The pass keeps the amounts it was sold at, so a later price change does not alter it.
	@Requirement("RIA-52")
	@Test
	void shouldKeepItsAmountsWhenTheBasePriceChangesLater() {
		var price = new PassPrice(PassType.ONE_DAY, 1000, 5000);
		var pass = Pass.purchase(GUEST, price);

		price.change(1200, 6000);

		assertThat(pass.getPriceCents()).isEqualTo(1000);
		assertThat(pass.getDepositCents()).isEqualTo(5000);
	}

	@Requirement("RIA-52")
	@Test
	void shouldGiveEachPassItsOwnId() {
		assertThat(Pass.purchase(GUEST, STAY_PRICE).getId()).isNotEqualTo(Pass.purchase(GUEST, STAY_PRICE).getId());
	}

	@Requirement("RIA-52")
	@Test
	void shouldRequireAGuest() {
		assertThatThrownBy(() -> Pass.purchase(null, STAY_PRICE)).isInstanceOf(IllegalArgumentException.class);
	}

	@Requirement("RIA-52")
	@Test
	void shouldBeOpenUntilClosed() {
		assertThat(PassStatus.INACTIVE.isOpen()).isTrue();
		assertThat(PassStatus.ACTIVE.isOpen()).isTrue();
		assertThat(PassStatus.CLOSED.isOpen()).isFalse();
	}

}
