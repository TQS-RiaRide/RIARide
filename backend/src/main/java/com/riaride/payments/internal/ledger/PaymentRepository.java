package com.riaride.payments.internal.ledger;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface PaymentRepository extends JpaRepository<Payment, UUID> {

  Optional<Payment> findByIdempotencyKey(String idempotencyKey);

  // A pass has one purchase payment; it holds the card saved for off-session charges.
  Optional<Payment> findFirstByPassIdAndKind(UUID passId, Payment.Kind kind);
}
