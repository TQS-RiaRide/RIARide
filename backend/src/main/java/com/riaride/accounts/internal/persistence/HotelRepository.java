package com.riaride.accounts.internal.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HotelRepository extends JpaRepository<Hotel, UUID> {

	Optional<Hotel> findByName(String name);

}
