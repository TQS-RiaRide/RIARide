package com.riaride.accounts.internal.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.getxray.xray.junit.customjunitxml.annotations.Requirement;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

// Unit test (Surefire): a stay lasts at least one night, so check-out must be after check-in.
class StayTest {

	private static final UUID MARES_PRAIA = UUID.fromString("00000000-0000-0000-0000-000000000002");
	private static final StayCode CODE = new StayCode("K7M3Q9TX");
	private static final LocalDate CHECK_IN = LocalDate.of(2026, 12, 1);

	@Requirement("RIA-50")
	@Test
	void shouldRegisterActiveStayWithGivenDetails() {
		var checkOut = LocalDate.of(2026, 12, 5);

		var stay = Stay.register(MARES_PRAIA, "Rita Almeida", CODE, CHECK_IN, checkOut);

		assertThat(stay.getHotelId()).isEqualTo(MARES_PRAIA);
		assertThat(stay.getGuestName()).isEqualTo("Rita Almeida");
		assertThat(stay.getStayCode()).isEqualTo(CODE);
		assertThat(stay.getCheckIn()).isEqualTo(CHECK_IN);
		assertThat(stay.getCheckOut()).isEqualTo(checkOut);
		assertThat(stay.getStatus()).isEqualTo(StayStatus.ACTIVE);
		assertThat(stay.getId()).isNotNull();
	}

	@Requirement("RIA-50")
	@Test
	void shouldAcceptStayOfExactlyOneNight() {
		var stay = Stay.register(MARES_PRAIA, "Rita Almeida", CODE, CHECK_IN, CHECK_IN.plusDays(1));

		assertThat(stay.getCheckOut()).isEqualTo(LocalDate.of(2026, 12, 2));
	}

	@Requirement("RIA-50")
	@Test
	void shouldRejectStayWhenCheckOutIsSameDayAsCheckIn() {
		assertThatThrownBy(() -> Stay.register(MARES_PRAIA, "Rita Almeida", CODE, CHECK_IN, CHECK_IN))
			.isInstanceOf(InvalidStayDatesException.class);
	}

	@Requirement("RIA-50")
	@Test
	void shouldRejectStayWhenCheckOutIsBeforeCheckIn() {
		var checkOut = CHECK_IN.minusDays(1);

		assertThatThrownBy(() -> Stay.register(MARES_PRAIA, "Rita Almeida", CODE, CHECK_IN, checkOut))
			.isInstanceOf(InvalidStayDatesException.class);
	}

}
