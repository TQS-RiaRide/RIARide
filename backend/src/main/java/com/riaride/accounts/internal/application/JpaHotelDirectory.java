package com.riaride.accounts.internal.application;

import com.riaride.accounts.HotelDirectory;
import com.riaride.accounts.HotelSummary;
import com.riaride.accounts.internal.persistence.HotelRepository;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class JpaHotelDirectory implements HotelDirectory {

	private final HotelRepository hotels;

	JpaHotelDirectory(HotelRepository hotels) {
		this.hotels = hotels;
	}

	@Override
	@Transactional(readOnly = true)
	public Optional<HotelSummary> findByName(String name) {
		return hotels.findByName(name).map(hotel -> new HotelSummary(hotel.getId(), hotel.getName()));
	}

}
