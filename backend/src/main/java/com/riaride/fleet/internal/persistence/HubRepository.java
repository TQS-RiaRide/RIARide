package com.riaride.fleet.internal.persistence;

import com.riaride.fleet.internal.domain.Hub;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HubRepository extends JpaRepository<Hub, UUID> {

  // Exact match: names that differ only in letter case are different hubs.
  boolean existsByName(String name);
}
