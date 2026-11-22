package com.riaride.passes.internal.persistence;

import com.riaride.passes.internal.domain.PassPrice;
import com.riaride.passes.internal.domain.PassType;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PassPriceRepository extends JpaRepository<PassPrice, PassType> {
}
