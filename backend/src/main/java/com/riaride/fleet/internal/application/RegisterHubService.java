package com.riaride.fleet.internal.application;

import com.riaride.accounts.HotelDirectory;
import com.riaride.accounts.HotelSummary;
import com.riaride.fleet.internal.domain.Hub;
import com.riaride.fleet.internal.domain.InvalidHubException;
import com.riaride.fleet.internal.persistence.HubRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Registers a hub at a hotel of the group or at a tourist spot (RIA-56).
@Service
public class RegisterHubService {

	private final HubRepository hubs;
	private final HotelDirectory hotels;

	public RegisterHubService(HubRepository hubs, HotelDirectory hotels) {
		this.hubs = hubs;
		this.hotels = hotels;
	}

	// The unique constraint on hub.name is the final guard against a concurrent duplicate: the
	// insert is flushed here, so its DataIntegrityViolationException reaches the caller.
	@Transactional
	public RegisteredHub register(RegisterHubCommand command) {
		var hotel = command.hotelName() == null ? null : hotelNamed(command.hotelName());
		var hub = Hub.register(command.name(), command.kind(), hotel == null ? null : hotel.id(), command.latitude(),
			command.longitude(), command.capacity());
		if (hubs.existsByName(hub.getName())) {
			throw new HubNameAlreadyInUseException();
		}
		return new RegisteredHub(hubs.saveAndFlush(hub), hotel == null ? null : hotel.name());
	}

	private HotelSummary hotelNamed(String name) {
		return hotels.findByName(name)
			.orElseThrow(() -> new InvalidHubException("hotel", "No hotel of the group has this name."));
	}

}
