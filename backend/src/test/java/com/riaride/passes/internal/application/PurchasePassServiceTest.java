package com.riaride.passes.internal.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
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
import com.riaride.payments.RefundRequest;
import com.riaride.payments.RefundResult;
import com.riaride.payments.TransientPaymentException;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

// Unit test (Surefire): buying a pass checks the stay (BR1) and the open pass, reserves the pass
// before charging, charges the price and deposit once (BR3, BR5), and leaves no charge without a
// pass: a failed payment releases the reservation, and a failed store after the charge refunds it.
@ExtendWith(MockitoExtension.class)
class PurchasePassServiceTest {

	private static final String KEYCLOAK_ID = "8d1f7c52-3c1e-4a8e-9b0e-2f4f6d7a9c11";
	private static final UUID GUEST = UUID.fromString("5b7f0c2e-0d1a-4a7e-9a55-3c2d1e0f4a11");
	private static final ActiveGuest ACTIVE_GUEST = new ActiveGuest(GUEST, UUID.randomUUID(), LocalDate.of(2026, 12, 2));
	private static final String KEY = "purchase-7f3c2a";
	private static final String SCOPED_KEY = GUEST + ":" + KEY;
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
		when(guests.findGuestId(KEYCLOAK_ID)).thenReturn(Optional.of(GUEST));
		when(ledger.findPurchase(SCOPED_KEY)).thenReturn(Optional.empty());
		when(guests.findActiveGuest(KEYCLOAK_ID)).thenReturn(Optional.of(ACTIVE_GUEST));
		when(prices.findById(type)).thenReturn(Optional.of(new PassPrice(type, priceCents, depositCents)));
		when(passes.existsByGuestIdAndStatusIn(GUEST, PassStatus.OPEN)).thenReturn(false);
	}

	@Requirement("RIA-52")
	@Test
	void shouldReserveThenChargeOnceThenConfirmTheInactivePass() {
		givenGuestCanBuy(PassType.STAY, 2500, 5000);
		var request = ArgumentCaptor.forClass(PurchaseRequest.class);
		when(gateway.purchase(request.capture())).thenReturn(PAID);

		var pass = service.purchase(buy(PassType.STAY));

		var order = inOrder(book, gateway);
		order.verify(book).reserve(pass);
		order.verify(gateway).purchase(any());
		order.verify(book).confirm(pass, SCOPED_KEY, PAID);
		assertThat(request.getValue().amount().cents()).isEqualTo(7500);
		assertThat(request.getValue().paymentMethod()).isEqualTo("pm_card_visa");
		assertThat(request.getValue().idempotencyKey()).isEqualTo(SCOPED_KEY);
		assertThat(request.getValue().reference()).isEqualTo("pass-" + pass.getId());
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

	@Requirement("RIA-52")
	@Test
	void shouldRefuseAUserWhoIsNotAGuest() {
		when(guests.findGuestId(KEYCLOAK_ID)).thenReturn(Optional.empty());
		var command = buy(PassType.STAY);

		assertThatThrownBy(() -> service.purchase(command)).isInstanceOf(NoActiveStayException.class);
		verifyNoInteractions(gateway, book, ledger);
	}

	// BR1: only guests with an active stay can buy a pass.
	@Requirement("RIA-52")
	@Test
	void shouldRefuseAGuestWithoutActiveStay() {
		when(guests.findGuestId(KEYCLOAK_ID)).thenReturn(Optional.of(GUEST));
		when(ledger.findPurchase(SCOPED_KEY)).thenReturn(Optional.empty());
		when(guests.findActiveGuest(KEYCLOAK_ID)).thenReturn(Optional.empty());
		var command = buy(PassType.STAY);

		assertThatThrownBy(() -> service.purchase(command)).isInstanceOf(NoActiveStayException.class);
		verifyNoInteractions(gateway, book);
	}

	// Product Owner decision: one open pass per guest.
	@Requirement("RIA-52")
	@Test
	void shouldRefuseAGuestWhoAlreadyHasAnOpenPass() {
		givenGuestCanBuy(PassType.STAY, 2500, 5000);
		when(passes.existsByGuestIdAndStatusIn(GUEST, PassStatus.OPEN)).thenReturn(true);
		var command = buy(PassType.STAY);

		assertThatThrownBy(() -> service.purchase(command)).isInstanceOf(PassAlreadyOpenException.class);
		verifyNoInteractions(gateway, book);
	}

	// Two purchases at once with different keys: the reservation of the second one hits the
	// one-open-pass index before its card is charged.
	@Requirement("RIA-52")
	@Test
	void shouldRefuseWithoutChargingWhenAConcurrentPurchaseReservedFirst() {
		givenGuestCanBuy(PassType.STAY, 2500, 5000);
		doThrow(new DataIntegrityViolationException("uq_pass_open_per_guest")).when(book).reserve(any(Pass.class));
		var command = buy(PassType.STAY);

		assertThatThrownBy(() -> service.purchase(command)).isInstanceOf(PassAlreadyOpenException.class);
		verifyNoInteractions(gateway);
	}

	@Requirement("RIA-52")
	@Test
	void shouldReleaseTheReservationWhenTheCardIsDeclined() {
		givenGuestCanBuy(PassType.STAY, 2500, 5000);
		var declined = new PermanentPaymentException("card_declined", "Stripe request failed", null);
		when(gateway.purchase(any())).thenThrow(declined);
		var command = buy(PassType.STAY);

		assertThatThrownBy(() -> service.purchase(command)).isSameAs(declined);
		verify(book).release(any(Pass.class));
		verify(book, never()).confirm(any(), any(), any());
	}

	@Requirement("RIA-52")
	@Test
	void shouldReleaseTheReservationWhenThePaymentFailsTransiently() {
		givenGuestCanBuy(PassType.STAY, 2500, 5000);
		var timeout = new TransientPaymentException("network_error", "Stripe request failed", null);
		when(gateway.purchase(any())).thenThrow(timeout);
		var command = buy(PassType.STAY);

		assertThatThrownBy(() -> service.purchase(command)).isSameAs(timeout);
		verify(book).release(any(Pass.class));
	}

	// The card was charged but the pass could not be stored: refund it, so no charge is left
	// without a pass.
	@Requirement("RIA-52")
	@Test
	void shouldRefundTheChargeWhenThePassCannotBeStoredAfterwards() {
		givenGuestCanBuy(PassType.STAY, 2500, 5000);
		when(gateway.purchase(any())).thenReturn(PAID);
		var failure = new IllegalStateException("database down");
		doThrow(failure).when(book).confirm(any(Pass.class), any(), any());
		var refund = ArgumentCaptor.forClass(RefundRequest.class);
		when(gateway.refund(refund.capture())).thenReturn(new RefundResult("re_1", false));
		var command = buy(PassType.STAY);

		assertThatThrownBy(() -> service.purchase(command)).isInstanceOf(PassNotCreatedException.class).hasCause(failure);
		assertThat(refund.getValue().paymentRef()).isEqualTo("pi_purchase");
		assertThat(refund.getValue().amount().cents()).isEqualTo(7500);
		assertThat(refund.getValue().idempotencyKey()).isEqualTo(SCOPED_KEY + ":refund");
	}

	@Requirement("RIA-52")
	@Test
	void shouldKeepTheOriginalErrorWhenTheRefundAlsoFails() {
		givenGuestCanBuy(PassType.STAY, 2500, 5000);
		when(gateway.purchase(any())).thenReturn(PAID);
		var failure = new IllegalStateException("database down");
		doThrow(failure).when(book).confirm(any(Pass.class), any(), any());
		var refundFailure = new TransientPaymentException("network_error", "Stripe request failed", null);
		when(gateway.refund(any())).thenThrow(refundFailure);
		var command = buy(PassType.STAY);

		assertThatThrownBy(() -> service.purchase(command))
			.isInstanceOf(PassNotCreatedException.class)
			.hasCause(failure)
			.satisfies(thrown -> assertThat(thrown.getSuppressed()).containsExactly(refundFailure));
	}

	@Requirement("RIA-52")
	@Test
	void shouldRefuseWhenTheTypeHasNoBasePrice() {
		when(guests.findGuestId(KEYCLOAK_ID)).thenReturn(Optional.of(GUEST));
		when(ledger.findPurchase(SCOPED_KEY)).thenReturn(Optional.empty());
		when(guests.findActiveGuest(KEYCLOAK_ID)).thenReturn(Optional.of(ACTIVE_GUEST));
		when(prices.findById(PassType.THREE_DAY)).thenReturn(Optional.empty());
		var command = buy(PassType.THREE_DAY);

		assertThatThrownBy(() -> service.purchase(command)).isInstanceOf(PassPriceNotSetException.class);
		verifyNoInteractions(gateway, book);
	}

	// The same key never charges twice, and a replay works even if the stay has ended since.
	@Requirement("RIA-52")
	@Test
	void shouldReturnThePassAlreadyBoughtWithTheSameKeyWithoutChargingAgain() {
		var existing = Pass.purchase(GUEST, new PassPrice(PassType.STAY, 2500, 5000), SCOPED_KEY);
		when(guests.findGuestId(KEYCLOAK_ID)).thenReturn(Optional.of(GUEST));
		when(ledger.findPurchase(SCOPED_KEY)).thenReturn(Optional.of(new RecordedPurchase(existing.getId(), "pi_purchase")));
		when(passes.findById(existing.getId())).thenReturn(Optional.of(existing));

		var pass = service.purchase(buy(PassType.STAY));

		assertThat(pass).isSameAs(existing);
		verify(guests, never()).findActiveGuest(any());
		verifyNoInteractions(gateway, book);
	}

	// Keys are scoped by guest, so another guest's key can neither be replayed nor detected.
	@Requirement("RIA-52")
	@Test
	void shouldLookUpTheKeyScopedToTheGuest() {
		givenGuestCanBuy(PassType.STAY, 2500, 5000);
		when(gateway.purchase(any())).thenReturn(PAID);

		service.purchase(buy(PassType.STAY));

		verify(ledger).findPurchase(SCOPED_KEY);
	}

	@Requirement("RIA-52")
	@Test
	void shouldRefuseTheSameKeyForADifferentPassType() {
		var existing = Pass.purchase(GUEST, new PassPrice(PassType.STAY, 2500, 5000), SCOPED_KEY);
		when(guests.findGuestId(KEYCLOAK_ID)).thenReturn(Optional.of(GUEST));
		when(ledger.findPurchase(SCOPED_KEY)).thenReturn(Optional.of(new RecordedPurchase(existing.getId(), "pi_purchase")));
		when(passes.findById(existing.getId())).thenReturn(Optional.of(existing));
		var command = buy(PassType.ONE_DAY);

		assertThatThrownBy(() -> service.purchase(command)).isInstanceOf(IdempotencyKeyReusedException.class);
		verifyNoInteractions(gateway, book);
	}

}
