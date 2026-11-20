package com.riaride.accounts.internal.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import app.getxray.xray.junit.customjunitxml.annotations.Requirement;
import com.riaride.accounts.internal.domain.Guest;
import com.riaride.accounts.internal.domain.Stay;
import com.riaride.accounts.internal.domain.StayAlreadyEndedException;
import com.riaride.accounts.internal.domain.StayCode;
import com.riaride.accounts.internal.persistence.GuestRepository;
import com.riaride.accounts.internal.persistence.StayRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

// Unit test (Surefire): a guest signs up with a stay code only while the stay is active (BR1),
// once per stay code, and no Keycloak user is left behind when the local insert fails.
@ExtendWith(MockitoExtension.class)
class SignUpGuestServiceTest {

	private static final ZoneId VILA_RIA = ZoneId.of("Europe/Lisbon");
	private static final UUID MARES_CENTRO = UUID.fromString("00000000-0000-0000-0000-000000000001");
	private static final StayCode CODE = new StayCode("K7M3Q9TX");
	private static final LocalDate CHECK_IN = LocalDate.of(2026, 12, 1);
	private static final LocalDate CHECK_OUT = LocalDate.of(2026, 12, 5);
	private static final String EMAIL = "rita.almeida@example.test";
	private static final String PASSWORD = "Sunny-Ria-2026";
	private static final String KEYCLOAK_ID = "8d1f7c52-3c1e-4a8e-9b0e-2f4f6d7a9c11";

	@Mock
	private StayRepository stays;

	@Mock
	private GuestRepository guests;

	@Mock
	private IdentityProvider identity;

	// Vila Ria is on UTC in December, so these instants are also local times.
	private SignUpGuestService serviceAt(String instant) {
		return new SignUpGuestService(stays, guests, identity, Clock.fixed(Instant.parse(instant), VILA_RIA));
	}

	private SignUpGuestService service() {
		return serviceAt("2026-12-02T10:00:00Z");
	}

	private static Stay activeStay() {
		return Stay.register(MARES_CENTRO, "Rita Almeida", CODE, CHECK_IN, CHECK_OUT, CHECK_IN);
	}

	private static SignUpGuestCommand signUpWith(String stayCode) {
		return new SignUpGuestCommand(EMAIL, PASSWORD, stayCode, "pt");
	}

	private void givenStayIsFreeToUse(Stay stay) {
		when(stays.findByStayCode(CODE)).thenReturn(Optional.of(stay));
		when(guests.existsByStayId(stay.getId())).thenReturn(false);
		when(guests.existsByEmail(EMAIL)).thenReturn(false);
	}

	@Requirement("RIA-51")
	@Test
	void shouldCreateAccountLinkedToStayWhenStayIsActive() {
		var stay = activeStay();
		givenStayIsFreeToUse(stay);
		when(identity.createGuestUser(any())).thenReturn(KEYCLOAK_ID);
		when(guests.saveAndFlush(any(Guest.class))).thenAnswer(invocation -> invocation.getArgument(0));

		var guest = service().signUp(signUpWith("K7M3Q9TX"));

		assertThat(guest.getKeycloakId()).isEqualTo(KEYCLOAK_ID);
		assertThat(guest.getStayId()).isEqualTo(stay.getId());
		assertThat(guest.getEmail()).isEqualTo(EMAIL);
		assertThat(guest.getLocale()).isEqualTo("pt");
	}

	@Requirement("RIA-51")
	@Test
	void shouldCreateKeycloakUserWithGuestNameFromStayAndGivenPassword() {
		givenStayIsFreeToUse(activeStay());
		when(identity.createGuestUser(any())).thenReturn(KEYCLOAK_ID);
		when(guests.saveAndFlush(any(Guest.class))).thenAnswer(invocation -> invocation.getArgument(0));
		var user = ArgumentCaptor.forClass(NewGuestUser.class);

		service().signUp(signUpWith("K7M3Q9TX"));

		verify(identity).createGuestUser(user.capture());
		assertThat(user.getValue().email()).isEqualTo(EMAIL);
		assertThat(user.getValue().password()).isEqualTo(PASSWORD);
		assertThat(user.getValue().firstName()).isEqualTo("Rita");
		assertThat(user.getValue().lastName()).isEqualTo("Almeida");
	}

	@Requirement("RIA-51")
	@Test
	void shouldNormalizeEmailAndStayCodeTypedByGuest() {
		givenStayIsFreeToUse(activeStay());
		when(identity.createGuestUser(any())).thenReturn(KEYCLOAK_ID);
		when(guests.saveAndFlush(any(Guest.class))).thenAnswer(invocation -> invocation.getArgument(0));

		var guest = service().signUp(new SignUpGuestCommand("  Rita.Almeida@Example.TEST ", PASSWORD, " k7m3q9tx ", "pt"));

		assertThat(guest.getEmail()).isEqualTo(EMAIL);
	}

	@Requirement("RIA-51")
	@Test
	void shouldAcceptSignUpAtTheLastMinuteOfTheCheckOutDayInVilaRia() {
		givenStayIsFreeToUse(activeStay());
		when(identity.createGuestUser(any())).thenReturn(KEYCLOAK_ID);
		when(guests.saveAndFlush(any(Guest.class))).thenAnswer(invocation -> invocation.getArgument(0));

		var guest = serviceAt("2026-12-05T23:59:59Z").signUp(signUpWith("K7M3Q9TX"));

		assertThat(guest.getKeycloakId()).isEqualTo(KEYCLOAK_ID);
	}

	@Requirement("RIA-51")
	@Test
	void shouldRefuseSignUpFromMidnightAfterTheCheckOutDayInVilaRia() {
		when(stays.findByStayCode(CODE)).thenReturn(Optional.of(activeStay()));
		var service = serviceAt("2026-12-06T00:00:00Z");
		var command = signUpWith("K7M3Q9TX");

		assertThatThrownBy(() -> service.signUp(command)).isInstanceOf(StayAlreadyEndedException.class);
		verifyNoInteractions(identity);
		verify(guests, never()).saveAndFlush(any());
	}

	@Requirement("RIA-51")
	@Test
	void shouldRefuseUnknownStayCodeWithGenericError() {
		when(stays.findByStayCode(CODE)).thenReturn(Optional.empty());
		var service = service();
		var command = signUpWith("K7M3Q9TX");

		assertThatThrownBy(() -> service.signUp(command)).isInstanceOf(InvalidStayCodeException.class);
		verifyNoInteractions(identity);
	}

	@Requirement("RIA-51")
	@Test
	void shouldRefuseMalformedStayCodeWithGenericError() {
		var service = service();
		var command = signUpWith("NOT-A-CODE");

		assertThatThrownBy(() -> service.signUp(command)).isInstanceOf(InvalidStayCodeException.class);
		verifyNoInteractions(stays, identity);
	}

	// Same error as an unknown code, so the response does not reveal that the code exists.
	@Requirement("RIA-51")
	@Test
	void shouldRefuseStayCodeAlreadyUsedWithSameGenericError() {
		var stay = activeStay();
		when(stays.findByStayCode(CODE)).thenReturn(Optional.of(stay));
		when(guests.existsByStayId(stay.getId())).thenReturn(true);
		var service = service();
		var command = signUpWith("K7M3Q9TX");

		assertThatThrownBy(() -> service.signUp(command)).isInstanceOf(InvalidStayCodeException.class);
		verifyNoInteractions(identity);
	}

	@Requirement("RIA-51")
	@Test
	void shouldRefuseEmailThatAlreadyHasAnAccount() {
		var stay = activeStay();
		when(stays.findByStayCode(CODE)).thenReturn(Optional.of(stay));
		when(guests.existsByStayId(stay.getId())).thenReturn(false);
		when(guests.existsByEmail(EMAIL)).thenReturn(true);
		var service = service();
		var command = signUpWith("K7M3Q9TX");

		assertThatThrownBy(() -> service.signUp(command)).isInstanceOf(EmailAlreadyRegisteredException.class);
		verifyNoInteractions(identity);
	}

	// ADR 0010: if the local insert fails after Keycloak created the user, the user is deleted.
	@Requirement("RIA-51")
	@Test
	void shouldDeleteKeycloakUserWhenSavingGuestFails() {
		givenStayIsFreeToUse(activeStay());
		when(identity.createGuestUser(any())).thenReturn(KEYCLOAK_ID);
		var failure = new DataIntegrityViolationException("duplicate key");
		when(guests.saveAndFlush(any(Guest.class))).thenThrow(failure);
		var service = service();
		var command = signUpWith("K7M3Q9TX");

		assertThatThrownBy(() -> service.signUp(command)).isSameAs(failure);
		verify(identity).deleteUser(KEYCLOAK_ID);
	}

	@Requirement("RIA-51")
	@Test
	void shouldNotShowPasswordWhenCommandIsPrinted() {
		assertThat(signUpWith("K7M3Q9TX").toString()).doesNotContain(PASSWORD);
		assertThat(new NewGuestUser(EMAIL, PASSWORD, "Rita", "Almeida").toString()).doesNotContain(PASSWORD);
	}

}
