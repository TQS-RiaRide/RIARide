package com.riaride.passes.internal.persistence;

import com.riaride.passes.internal.domain.UsageRule;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UsageRuleRepository extends JpaRepository<UsageRule, UUID> {

  // The rule in force at a moment: the latest one that applies from then or earlier. Flyway seeds
  // one from 1970, so there is always one.
  Optional<UsageRule> findFirstByEffectiveFromLessThanEqualOrderByEffectiveFromDesc(Instant at);
}
