package com.riaride.accounts.internal.persistence;

import com.riaride.accounts.internal.domain.Guest;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GuestRepository extends JpaRepository<Guest, UUID> {

	boolean existsByStayId(UUID stayId);

	boolean existsByEmail(String email);

}
