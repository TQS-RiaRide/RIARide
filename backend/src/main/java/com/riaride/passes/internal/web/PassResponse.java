package com.riaride.passes.internal.web;

import com.riaride.passes.internal.domain.Pass;
import com.riaride.passes.internal.domain.PassStatus;
import com.riaride.passes.internal.domain.PassType;
import com.riaride.payments.Money;
import java.util.Locale;
import java.util.UUID;

record PassResponse(UUID id, PassType passType, PassStatus status, int priceCents, int depositCents, String currency) {

	static PassResponse from(Pass pass) {
		return new PassResponse(pass.getId(), pass.getPassType(), pass.getStatus(), pass.getPriceCents(),
			pass.getDepositCents(), Money.CURRENCY.toUpperCase(Locale.ROOT));
	}

}
