package com.riaride.fleet.internal.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import app.getxray.xray.junit.customjunitxml.annotations.Requirement;
import com.riaride.fleet.internal.domain.Hub;
import com.riaride.fleet.internal.domain.HubKind;
import com.riaride.fleet.internal.domain.InvalidVehicleException;
import com.riaride.fleet.internal.domain.Vehicle;
import com.riaride.fleet.internal.domain.VehicleStatus;
import com.riaride.fleet.internal.domain.VehicleType;
import com.riaride.fleet.internal.persistence.HubRepository;
import com.riaride.fleet.internal.persistence.VehicleRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

// Unit test (Surefire): registering a vehicle at a hub (RIA-57). BR10: a hub holds at most its
// capacity, so the vehicle is refused when every slot is taken. The code must be unique, and the
// hub must exist. The concurrent cases (hub row lock, unique constraint) are in RegisterVehicleApiIT.
@ExtendWith(MockitoExtension.class)
class RegisterVehicleServiceTest {

	private static final int CAPACITY = 8;

	@Mock
	private HubRepository hubs;

	@Mock
	private VehicleRepository vehicles;

	private RegisterVehicleService service;

	private Hub salinas;

	@BeforeEach
	void setUp() {
		service = new RegisterVehicleService(hubs, vehicles);
		salinas = Hub.register("Marés Salinas", HubKind.HOTEL, UUID.randomUUID(), 40.6510, -8.6820, CAPACITY);
	}

	private RegisterVehicleCommand bikeAtSalinas(String code) {
		return new RegisterVehicleCommand(salinas.getId(), code, VehicleType.E_BIKE);
	}

	private void givenSalinasWithDockedBikes(long docked) {
		when(hubs.findByIdForUpdate(salinas.getId())).thenReturn(Optional.of(salinas));
		when(vehicles.existsByCodeIgnoreCase("EB-401")).thenReturn(false);
		when(vehicles.countByHubId(salinas.getId())).thenReturn(docked);
	}

	@Requirement("RIA-57")
	@ParameterizedTest
	@ValueSource(longs = { 0, 3, CAPACITY - 1 })
	void shouldRegisterAvailableBikeAtHubWithAFreeSlot(long docked) {
		givenSalinasWithDockedBikes(docked);
		when(vehicles.saveAndFlush(any(Vehicle.class))).thenAnswer(invocation -> invocation.getArgument(0));

		var vehicle = service.register(bikeAtSalinas("EB-401"));

		assertThat(vehicle.getCode()).isEqualTo("EB-401");
		assertThat(vehicle.getStatus()).isEqualTo(VehicleStatus.AVAILABLE);
		assertThat(vehicle.getHubId()).isEqualTo(salinas.getId());
		verify(vehicles).saveAndFlush(vehicle);
	}

	// BR10 boundary: 7 of 8 slots taken is accepted (above); 8 of 8 is refused.
	@Requirement("RIA-57")
	@ParameterizedTest
	@ValueSource(longs = { CAPACITY, CAPACITY + 1 })
	void shouldRefuseBikeAtFullHub(long docked) {
		givenSalinasWithDockedBikes(docked);

		assertThatThrownBy(() -> service.register(bikeAtSalinas("EB-401"))).isInstanceOf(HubFullException.class);

		verify(vehicles, never()).saveAndFlush(any());
	}

	@Requirement("RIA-57")
	@Test
	void shouldRefuseCodeAlreadyUsedByAnotherVehicle() {
		when(hubs.findByIdForUpdate(salinas.getId())).thenReturn(Optional.of(salinas));
		when(vehicles.existsByCodeIgnoreCase("EB-401")).thenReturn(true);

		assertThatThrownBy(() -> service.register(bikeAtSalinas("EB-401")))
			.isInstanceOf(VehicleCodeAlreadyInUseException.class);

		verify(vehicles, never()).saveAndFlush(any());
	}

	// A code is in use whatever its case: eb-401 is refused when EB-401 exists.
	@Requirement("RIA-57")
	@Test
	void shouldRefuseCodeThatDiffersOnlyInCase() {
		when(hubs.findByIdForUpdate(salinas.getId())).thenReturn(Optional.of(salinas));
		when(vehicles.existsByCodeIgnoreCase("eb-401")).thenReturn(true);

		assertThatThrownBy(() -> service.register(bikeAtSalinas("eb-401")))
			.isInstanceOf(VehicleCodeAlreadyInUseException.class);

		verify(vehicles, never()).saveAndFlush(any());
	}

	@Requirement("RIA-57")
	@Test
	void shouldRefuseUnknownHub() {
		var unknownHub = UUID.randomUUID();
		when(hubs.findByIdForUpdate(unknownHub)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.register(new RegisterVehicleCommand(unknownHub, "EB-401", VehicleType.E_BIKE)))
			.isInstanceOf(HubNotFoundException.class);

		verifyNoInteractions(vehicles);
	}

	// Invalid details are rejected before the hub row is locked.
	@Requirement("RIA-57")
	@Test
	void shouldRejectInvalidCodeWithoutLockingTheHub() {
		assertThatThrownBy(() -> service.register(bikeAtSalinas("EB 401")))
			.isInstanceOf(InvalidVehicleException.class);

		verifyNoInteractions(hubs, vehicles);
	}

}
