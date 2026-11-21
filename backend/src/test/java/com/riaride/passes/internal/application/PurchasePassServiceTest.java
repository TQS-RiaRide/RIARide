package com.riaride.passes.internal.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import app.getxray.xray.junit.customjunitxml.annotations.Requirement;
import com.riaride.accounts.ActiveGuest;
import com.riaride.accounts.GuestDirectory;
import com.riaride.passes.internal.domain.Pass;
import com.riaride.passes.internal.domain.PassPrice;
import com.riaride.passes.internal.domain.PassStatus;
import com.riaride.passes.internal.domain.PassType;
import com.riaride.passes.internal.persistence.PassPriceRepository;
import com.riaride.passes.internal.persistence.PassRepository;
import com.riaride.payments.PaymentGateway;
import com.riaride.payments.PaymentLedger;
import com.riaride.payments.PermanentPaymentException;
import com.riaride.payments.PurchaseRequest;
import com.riaride.payments.PurchaseResult;
import com.riaride.payments.RecordedPurchase;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

// Unit test (Surefire): buying a pass checks the stay (BR1) and the open pass, charges the price
// and deposit once (BR3, BR5), and creates no pass when the payment fails.
@ExtendWith(MockitoExtension.class)
class PurchasePassServiceTest {

	private static final String KEYCLOAK_ID = "8d1f7c52-3c1e-4a8e-9b0e-2f4f6d7a9c11";
	private static final UUID GUEST = UUID.fromString("5b7f0c2e-0d1a-4a7e-9a55-3c2d1e0f4a11");
	private static final ActiveGuest ACTIVE_GUEST = new ActiveGuest(GUEST, UUID.randomUUID(), LocalDate.of(2026, 12, 2));
	private static final String KEY = "purchase-7f3c2a";
	private static final PurchaseResult PAID = new PurchaseResult("pi_purchase", "cus_rita", "pm_saved_card");

	@Mock
	private GuestDirectory guests;

	@Mock
	private PassPriceRepository prices;

	@Mock
	private PassRepository passes;

	@Mock
	private PaymentGateway gateway;

	@Mock
	private PaymentLedger ledger;

	@Mock
	private PassBook book;

	private PurchasePassService service;

	@BeforeEach
	void setUp() {
		service = new PurchasePassService(guests, prices, passes, gateway, ledger, book);
	}

	private static PurchasePassCommand buy(PassType type) {
		return new PurchasePassCommand(KEYCLOAK_ID, type, "pm_card_visa", KEY);
	}

	private void givenGuestCanBuy(PassType type, int priceCents, int depositCents) {
		when(ledger.findPurchase(KEY)).thenReturn(Optional.empty());
		when(guests.findActiveGuest(KEYCLOAK_ID)).thenReturn(Optional.of(ACTIVE_GUEST));
		when(passes.existsByGuestIdAndStatusIn(GUEST, PassStatus.OPEN)).thenReturn(false);
		when(prices.findById(type)).thenReturn(Optional.of(new PassPrice(type, priceCents, depositCents)));
	}

	@Requirement("RIA-52")
	@Test
	void shouldChargePriceAndDepositOnceAndStoreAnInactivePass() {
		givenGuestCanBuy(PassType.STAY, 2500, 5000);
		var request = ArgumentCaptor.forClass(PurchaseRequest.class);
		when(gateway.purchase(request.capture())).thenReturn(PAID);

		var pass = service.purchase(buy(PassType.STAY));

		assertThat(pass.getStatus()).isEqualTo(PassStatus.INACTIVE);
		assertThat(request.getValue().amount().cents()).isEqualTo(7500);
		assertThat(request.getValue().paymentMethod()).isEqualTo("pm_card_visa");
		assertThat(request.getValue().idempotencyKey()).isEqualTo(KEY);
		assertThat(request.getValue().reference()).isEqualTo("pass-" + pass.getId());
		verify(book).store(pass, KEY, PAID);
	}

	@Requirement("RIA-52")
	@Test
	void shouldUseTheBasePriceOfTheChosenType() {
		givenGuestCanBuy(PassType.ONE_DAY, 1000, 5000);
		var request = ArgumentCaptor.forClass(PurchaseRequest.class);
		when(gateway.purchase(request.capture())).thenReturn(PAID);

		var pass = service.purchase(buy(PassType.ONE_DAY));

		assertThat(pass.getPassType()).isEqualTo(PassType.ONE_DAY);
		assertThat(request.getValue().amount().cents()).isEqualTo(6000);
	}

	// BR1: only guests with an active stay can buy a pass.
	@Requirement("RIA-52")
	@Test
	void shouldRefuseGuestWithoutActiveStay() {
		when(ledger.findPurchase(KEY)).thenReturn(Optional.empty());
		when(guests.findActiveGuest(KEYCLOAK_ID)).thenReturn(Optional.empty());
		var command = buy(PassType.STAY);

		assertThatThrownBy(() -> service.purchase(command)).isInstanceOf(NoActiveStayException.class);
		verifyNoInteractions(gateway, book);
	}

	// Product Owner decision: one open pass per guest.
	@Requirement("RIA-52")
	@Test
	void shouldRefuseGuestWhoAlreadyHasAnOpenPass() {
		when(ledger.findPurchase(KEY)).thenReturn(Optional.empty());
		when(guests.findActiveGuest(KEYCLOAK_ID)).thenReturn(Optional.of(ACTIVE_GUEST));
		when(passes.existsByGuestIdAndStatusIn(GUEST, PassStatus.OPEN)).thenReturn(true);
		var command = buy(PassType.STAY);

		assertThatThrownBy(() -> service.purchase(command)).isInstanceOf(PassAlreadyOpenException.class);
		verifyNoInteractions(gateway, book);
	}

	@Requirement("RIA-52")
	@Test
	void shouldCreateNoPassWhenTheCardIsDeclined() {
		givenGuestCanBuy(PassType.STAY, 2500, 5000);
		var declined = new PermanentPaymentException("card_declined", "Stripe request failed", null);
		when(gateway.purchase(any())).thenThrow(declined);
		var command = buy(PassType.STAY);

		assertThatThrownBy(() -> service.purchase(command)).isSameAs(declined);
		verifyNoInteractions(book);
	}

	@Requirement("RIA-52")
	@Test
	void shouldRefuseWhenTheTypeHasNoBasePrice() {
		when(ledger.findPurchase(KEY)).thenReturn(Optional.empty());
		when(guests.findActiveGuest(KEYCLOAK_ID)).thenReturn(Optional.of(ACTIVE_GUEST));
		when(passes.existsByGuestIdAndStatusIn(GUEST, PassStatus.OPEN)).thenReturn(false);
		when(prices.findById(PassType.THREE_DAY)).thenReturn(Optional.empty());
		var command = buy(PassType.THREE_DAY);

		assertThatThrownBy(() -> service.purchase(command)).isInstanceOf(PassPriceNotSetException.class);
		verifyNoInteractions(gateway, book);
	}

	// The same idempotency key never charges twice: a retried purchase returns the pass it created.
	@Requirement("RIA-52")
	@Test
	void shouldReturnThePassAlreadyBoughtWithTheSameKeyWithoutChargingAgain() {
		var existing = Pass.purchase(GUEST, new PassPrice(PassType.STAY, 2500, 5000));
		when(ledger.findPurchase(KEY)).thenReturn(Optional.of(new RecordedPurchase(existing.getId(), "pi_purchase")));
		when(passes.findById(existing.getId())).thenReturn(Optional.of(existing));
		when(guests.findActiveGuest(KEYCLOAK_ID)).thenReturn(Optional.of(ACTIVE_GUEST));

		var pass = service.purchase(buy(PassType.STAY));

		assertThat(pass).isSameAs(existing);
		verifyNoInteractions(gateway, book);
	}

	// A key used by another guest is not theirs to replay.
	@Requirement("RIA-52")
	@Test
	void shouldRefuseAKeyAlreadyUsedByAnotherGuest() {
		var someoneElses = Pass.purchase(UUID.randomUUID(), new PassPrice(PassType.STAY, 2500, 5000));
		when(ledger.findPurchase(KEY)).thenReturn(Optional.of(new RecordedPurchase(someoneElses.getId(), "pi_other")));
		when(passes.findById(someoneElses.getId())).thenReturn(Optional.of(someoneElses));
		when(guests.findActiveGuest(KEYCLOAK_ID)).thenReturn(Optional.of(ACTIVE_GUEST));
		var command = buy(PassType.STAY);

		assertThatThrownBy(() -> service.purchase(command)).isInstanceOf(IdempotencyKeyReusedException.class);
		verify(gateway, never()).purchase(any());
	}

}
