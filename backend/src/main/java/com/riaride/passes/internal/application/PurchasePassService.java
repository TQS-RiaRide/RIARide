package com.riaride.passes.internal.application;

import com.riaride.accounts.GuestDirectory;
import com.riaride.passes.internal.domain.Pass;
import com.riaride.passes.internal.domain.PassStatus;
import com.riaride.passes.internal.persistence.PassPriceRepository;
import com.riaride.passes.internal.persistence.PassRepository;
import com.riaride.payments.PaymentGateway;
import com.riaride.payments.PaymentLedger;
import com.riaride.payments.PurchaseRequest;
import org.springframework.stereotype.Service;

// Buys a pass for a guest with an active stay (BR1): charges its base price and deposit once
// (BR3, BR5) and stores it as inactive until the first trip (BR4). If the payment fails, no pass
// is stored. The provider call stays outside any database transaction; PassBook stores the pass
// and its payment together afterwards. A retry with the same idempotency key returns the pass it
// created instead of charging again.
@Service
public class PurchasePassService {

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
		var recorded = ledger.findPurchase(command.idempotencyKey());
		var guest = guests.findActiveGuest(command.keycloakId()).orElseThrow(NoActiveStayException::new);
		if (recorded.isPresent()) {
			var pass = passes.findById(recorded.get().passId())
				.orElseThrow(() -> new IllegalStateException("A recorded purchase has no pass"));
			if (!pass.getGuestId().equals(guest.guestId())) {
				throw new IdempotencyKeyReusedException();
			}
			return pass;
		}
		if (passes.existsByGuestIdAndStatusIn(guest.guestId(), PassStatus.OPEN)) {
			throw new PassAlreadyOpenException();
		}
		var price = prices.findById(command.passType()).orElseThrow(PassPriceNotSetException::new);
		var pass = Pass.purchase(guest.guestId(), price);
		var paid = gateway.purchase(new PurchaseRequest(pass.amountToCharge(), command.paymentMethod(),
			"pass-" + pass.getId(), command.idempotencyKey()));
		book.store(pass, command.idempotencyKey(), paid);
		return pass;
	}

}
