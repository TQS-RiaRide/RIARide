package com.riaride.accounts.internal.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.getxray.xray.junit.customjunitxml.annotations.Requirement;
import com.riaride.TestcontainersConfiguration;
import com.riaride.accounts.internal.domain.Stay;
import com.riaride.accounts.internal.domain.StayCode;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

// Integration test (Failsafe): Flyway schema and constraints on PostgreSQL in Testcontainers.
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
class StayRepositoryIT {

	private static final UUID MARES_PRAIA = UUID.fromString("00000000-0000-0000-0000-000000000002");
	private static final LocalDate CHECK_IN = LocalDate.of(2026, 12, 1);
	private static final LocalDate CHECK_OUT = LocalDate.of(2026, 12, 5);
	private static final LocalDate TODAY = LocalDate.of(2026, 11, 30);

	@Autowired
	private StayRepository stays;

	@Autowired
	private HotelRepository hotels;

	@Autowired
	private JdbcTemplate jdbc;

	@Requirement("RIA-50")
	@Test
	void shouldSeedTheThreeMaresHotels() {
		assertThat(hotels.findAll())
			.extracting(Hotel::getName)
			.containsExactlyInAnyOrder("Marés Centro", "Marés Praia", "Marés Salinas");
	}

	@Requirement("RIA-50")
	@Test
	void shouldPersistStayAndFindItsCode() {
		var code = new StayCode("K7M3Q9TX");
		var stay = Stay.register(MARES_PRAIA, "Rita Almeida", code, CHECK_IN, CHECK_OUT, TODAY);

		stays.saveAndFlush(stay);

		assertThat(stays.existsByStayCode(code)).isTrue();
		assertThat(stays.findById(stay.getId()))
			.get()
			.satisfies(found -> {
				assertThat(found.getGuestName()).isEqualTo("Rita Almeida");
				assertThat(found.getCheckIn()).isEqualTo(CHECK_IN);
				assertThat(found.getCheckOut()).isEqualTo(CHECK_OUT);
			});
	}

	@Requirement("RIA-50")
	@Test
	void shouldNotFindCodeThatWasNeverIssued() {
		assertThat(stays.existsByStayCode(new StayCode("ZZZZZZZZ"))).isFalse();
	}

	@Requirement("RIA-50")
	@Test
	void shouldRejectSecondStayWithSameCode() {
		var code = new StayCode("P4R8W2NZ");
		stays.saveAndFlush(Stay.register(MARES_PRAIA, "Rita Almeida", code, CHECK_IN, CHECK_OUT, TODAY));
		var duplicate = Stay.register(MARES_PRAIA, "Tomás Ferreira", code, CHECK_IN, CHECK_OUT, TODAY);

		assertThatThrownBy(() -> stays.saveAndFlush(duplicate)).isInstanceOf(DataIntegrityViolationException.class);
	}

	@Requirement("RIA-50")
	@Test
	void shouldRejectStayWithUnknownHotel() {
		var unknownHotel = UUID.fromString("00000000-0000-0000-0000-0000000000ff");
		var stay = Stay.register(unknownHotel, "Rita Almeida", new StayCode("H5J6K7L8"), CHECK_IN, CHECK_OUT, TODAY);

		assertThatThrownBy(() -> stays.saveAndFlush(stay)).isInstanceOf(DataIntegrityViolationException.class);
	}

	@Requirement("RIA-50")
	@Test
	void shouldRejectRowWhoseCheckOutIsNotAfterCheckIn() {
		assertThatThrownBy(() -> jdbc.update("""
				insert into stay (id, hotel_id, guest_name, stay_code, check_in, check_out, status, created_at)
				values (?, ?, 'Rita Almeida', 'M2N3P4Q5', ?, ?, 'ACTIVE', now())
				""", UUID.randomUUID(), MARES_PRAIA, CHECK_IN, CHECK_IN))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

}
