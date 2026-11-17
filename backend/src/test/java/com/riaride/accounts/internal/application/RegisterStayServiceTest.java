package com.riaride.accounts.internal.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import app.getxray.xray.junit.customjunitxml.annotations.Requirement;
import com.riaride.accounts.internal.domain.InvalidStayDatesException;
import com.riaride.accounts.internal.domain.Stay;
import com.riaride.accounts.internal.domain.StayCode;
import com.riaride.accounts.internal.domain.StayCodeGenerator;
import com.riaride.accounts.internal.domain.StayStatus;
import com.riaride.accounts.internal.persistence.HotelRepository;
import com.riaride.accounts.internal.persistence.StayRepository;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

// Unit test (Surefire): registering a stay checks the hotel, who registers it, and code uniqueness.
@ExtendWith(MockitoExtension.class)
class RegisterStayServiceTest {

	private static final UUID MARES_CENTRO = UUID.fromString("00000000-0000-0000-0000-000000000001");
	private static final UUID MARES_PRAIA = UUID.fromString("00000000-0000-0000-0000-000000000002");
	private static final StayCode FIRST_CODE = new StayCode("K7M3Q9TX");
	private static final StayCode SECOND_CODE = new StayCode("P4R8W2NZ");

	@Mock
	private HotelRepository hotels;

	@Mock
	private StayRepository stays;

	@Mock
	private StayCodeGenerator codes;

	private RegisterStayService service;

	@BeforeEach
	void setUp() {
		service = new RegisterStayService(hotels, stays, codes);
	}

	private static RegisterStayCommand stayAt(UUID hotelId) {
		return new RegisterStayCommand(hotelId, "Rita Almeida", LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 5));
	}

	@Requirement("RIA-50")
	@Test
	void shouldRegisterStayWhenManagerRegistersAtOwnHotel() {
		when(hotels.existsById(MARES_PRAIA)).thenReturn(true);
		when(codes.next()).thenReturn(FIRST_CODE);
		when(stays.existsByStayCode(FIRST_CODE)).thenReturn(false);
		when(stays.save(any(Stay.class))).thenAnswer(invocation -> invocation.getArgument(0));

		var stay = service.register(stayAt(MARES_PRAIA), StayRegistrar.managerOf(MARES_PRAIA));

		assertThat(stay.getHotelId()).isEqualTo(MARES_PRAIA);
		assertThat(stay.getGuestName()).isEqualTo("Rita Almeida");
		assertThat(stay.getStayCode()).isEqualTo(FIRST_CODE);
		assertThat(stay.getStatus()).isEqualTo(StayStatus.ACTIVE);
		verify(stays).save(stay);
	}

	@Requirement("RIA-50")
	@Test
	void shouldRegisterStayAtAnyHotelWhenRegistrarIsAdmin() {
		when(hotels.existsById(MARES_CENTRO)).thenReturn(true);
		when(codes.next()).thenReturn(FIRST_CODE);
		when(stays.save(any(Stay.class))).thenAnswer(invocation -> invocation.getArgument(0));

		var stay = service.register(stayAt(MARES_CENTRO), StayRegistrar.admin());

		assertThat(stay.getHotelId()).isEqualTo(MARES_CENTRO);
	}

	@Requirement("RIA-50")
	@Test
	void shouldRefuseWhenManagerRegistersAtAnotherHotel() {
		var command = stayAt(MARES_CENTRO);
		var managerOfPraia = StayRegistrar.managerOf(MARES_PRAIA);

		assertThatThrownBy(() -> service.register(command, managerOfPraia))
			.isInstanceOf(HotelAccessDeniedException.class);
		verify(stays, never()).save(any());
	}

	@Requirement("RIA-50")
	@Test
	void shouldRefuseWhenHotelDoesNotExist() {
		var unknownHotel = UUID.fromString("00000000-0000-0000-0000-0000000000ff");
		when(hotels.existsById(unknownHotel)).thenReturn(false);
		var command = stayAt(unknownHotel);
		var admin = StayRegistrar.admin();

		assertThatThrownBy(() -> service.register(command, admin)).isInstanceOf(HotelNotFoundException.class);
		verify(stays, never()).save(any());
	}

	@Requirement("RIA-50")
	@Test
	void shouldRefuseWhenCheckOutIsNotAfterCheckIn() {
		when(hotels.existsById(MARES_PRAIA)).thenReturn(true);
		when(codes.next()).thenReturn(FIRST_CODE);
		var command = new RegisterStayCommand(MARES_PRAIA, "Rita Almeida", LocalDate.of(2026, 12, 5), LocalDate.of(2026, 12, 1));
		var manager = StayRegistrar.managerOf(MARES_PRAIA);

		assertThatThrownBy(() -> service.register(command, manager)).isInstanceOf(InvalidStayDatesException.class);
		verify(stays, never()).save(any());
	}

	@Requirement("RIA-50")
	@Test
	void shouldGenerateAnotherCodeWhenFirstIsTaken() {
		when(hotels.existsById(MARES_PRAIA)).thenReturn(true);
		when(codes.next()).thenReturn(FIRST_CODE, SECOND_CODE);
		when(stays.existsByStayCode(FIRST_CODE)).thenReturn(true);
		when(stays.existsByStayCode(SECOND_CODE)).thenReturn(false);
		when(stays.save(any(Stay.class))).thenAnswer(invocation -> invocation.getArgument(0));

		var stay = service.register(stayAt(MARES_PRAIA), StayRegistrar.managerOf(MARES_PRAIA));

		assertThat(stay.getStayCode()).isEqualTo(SECOND_CODE);
	}

	@Requirement("RIA-50")
	@Test
	void shouldGiveUpAfterFiveTakenCodes() {
		when(hotels.existsById(MARES_PRAIA)).thenReturn(true);
		when(codes.next()).thenReturn(FIRST_CODE);
		when(stays.existsByStayCode(FIRST_CODE)).thenReturn(true);
		var command = stayAt(MARES_PRAIA);
		var manager = StayRegistrar.managerOf(MARES_PRAIA);

		assertThatThrownBy(() -> service.register(command, manager)).isInstanceOf(StayCodeUnavailableException.class);
		verify(codes, times(5)).next();
		verify(stays, never()).save(any());
	}

}
