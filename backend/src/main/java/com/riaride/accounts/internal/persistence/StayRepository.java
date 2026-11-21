package com.riaride.accounts.internal.persistence;

import com.riaride.accounts.internal.domain.Stay;
import com.riaride.accounts.internal.domain.StayCode;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StayRepository extends JpaRepository<Stay, UUID> {

	boolean existsByStayCode(StayCode stayCode);

	Optional<Stay> findByStayCode(StayCode stayCode);

}
