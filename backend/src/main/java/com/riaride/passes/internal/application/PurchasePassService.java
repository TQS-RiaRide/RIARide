package com.riaride.passes.internal.application;

import com.riaride.accounts.GuestDirectory;
import com.riaride.passes.internal.domain.Pass;
import com.riaride.passes.internal.domain.PassStatus;
import com.riaride.passes.internal.persistence.PassPriceRepository;
import com.riaride.passes.internal.persistence.PassRepository;
import com.riaride.payments.PaymentException;
import com.riaride.payments.PaymentGateway;
import com.riaride.payments.PaymentLedger;
import com.riaride.payments.PurchaseRequest;
import com.riaride.payments.PurchaseResult;
import com.riaride.payments.RefundRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import java.util.UUID;
import org.springframework.stereotype.Service;

// Buys a pass for a guest with an active stay (BR1): charges its base price and deposit once
// (BR3, BR5) and stores it as inactive until the first trip (BR4).
//
// No charge is ever left without a pass:
// - the pass is reserved (PENDING) before the charge, so the one-open-pass index refuses a
//   concurrent purchase before its card is charged;
// - a failed payment releases the reservation;
// - if the pass cannot be stored after the charge, the charge is refunded.
// The idempotency key is scoped by guest, and the pass id is derived from it, so a retry with the
// same key sends the provider the same parameters and gets its cached result, and a recorded
// purchase is returned instead of charging again.
@Service
public class PurchasePassService {

	private static final Logger log = LoggerFactory.getLogger(PurchasePassService.class);

	private final GuestDirectory guests;
	private final PassPriceRepository prices;
	private final PassRepository passes;
	private final PaymentGateway gateway;
	private final PaymentLedger ledger;
	private final PassBook book;

	public PurchasePassService(GuestDirectory guests, PassPriceRepository prices, PassRepository passes,
		PaymentGateway gateway, PaymentLedger ledger, PassBook book) {
		this.guests = guests;
		this.prices = prices;
		this.passes = passes;
		this.gateway = gateway;
		this.ledger = ledger;
		this.book = book;
	}

	public Pass purchase(PurchasePassCommand command) {
		var guestId = guests.findGuestId(command.keycloakId()).orElseThrow(NoActiveStayException::new);
		var key = guestId + ":" + command.idempotencyKey();
		var recorded = ledger.findPurchase(key);
		if (recorded.isPresent()) {
			var pass = passes.findById(recorded.get().passId())
				.orElseThrow(() -> new IllegalStateException("A recorded purchase has no pass"));
			if (pass.getPassType() != command.passType()) {
				throw new IdempotencyKeyReusedException();
			}
			return pass;
		}
		guests.findActiveGuest(command.keycloakId()).orElseThrow(NoActiveStayException::new);
		var price = prices.findById(command.passType()).orElseThrow(PassPriceNotSetException::new);
		var pass = Pass.purchase(guestId, price, key);
		var retryOfPendingPurchase = passes.findById(pass.getId())
			.filter(existing -> existing.getStatus() == PassStatus.PENDING)
			.isPresent();
		// Only the request that created the reservation may release it: a retry that arrives while
		// the first request is still running finds the reservation and leaves it alone.
		var reservedHere = !retryOfPendingPurchase;
		if (reservedHere) {
			reserve(pass, guestId);
		}
		var paid = charge(pass, command, key, reservedHere);
		try {
			book.confirm(pass, key, paid);
		} catch (RuntimeException failure) {
			// A concurrent request with the same key may have confirmed this very purchase first:
			// then it is a duplicate, not a failure, and the charge must not be refunded.
			var confirmedConcurrently = ledger.findPurchase(key).flatMap(purchase -> passes.findById(purchase.passId()));
			if (confirmedConcurrently.isPresent()) {
				return confirmedConcurrently.get();
			}
			var notCreated = new PassNotCreatedException(failure);
			refund(pass, paid, key, notCreated, reservedHere);
			throw notCreated;
		}
		return pass;
	}

	private void reserve(Pass pass, UUID guestId) {
		if (passes.existsByGuestIdAndStatusIn(guestId, PassStatus.OPEN)) {
			throw new PassAlreadyOpenException();
		}
		try {
			book.reserve(pass);
		} catch (DataIntegrityViolationException concurrentPurchase) {
			throw new PassAlreadyOpenException();
		}
	}

	private PurchaseResult charge(Pass pass, PurchasePassCommand command, String key, boolean reservedHere) {
		try {
			return gateway.purchase(new PurchaseRequest(pass.amountToCharge(), command.paymentMethod(),
				"pass-" + pass.getId(), key));
		} catch (PaymentException failure) {
			if (reservedHere) {
				book.releasePending(pass);
			}
			throw failure;
		}
	}

	private void refund(Pass pass, PurchaseResult paid, String key, RuntimeException failure, boolean reservedHere) {
		log.error("Pass {} could not be stored after payment {}; refunding it", pass.getId(), paid.paymentRef());
		try {
			gateway.refund(new RefundRequest(paid.paymentRef(), pass.amountToCharge(), key + ":refund"));
		} catch (RuntimeException refundFailure) {
			failure.addSuppressed(refundFailure);
			log.error("Refund of payment {} failed; refund it by hand", paid.paymentRef());
		}
		if (!reservedHere) {
			return;
		}
		try {
			book.releasePending(pass);
		} catch (RuntimeException releaseFailure) {
			failure.addSuppressed(releaseFailure);
		}
	}

}
