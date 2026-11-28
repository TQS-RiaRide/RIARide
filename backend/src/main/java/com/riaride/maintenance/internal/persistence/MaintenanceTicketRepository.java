package com.riaride.maintenance.internal.persistence;

import com.riaride.maintenance.internal.domain.MaintenanceTicket;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MaintenanceTicketRepository extends JpaRepository<MaintenanceTicket, UUID> {}
