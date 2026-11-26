package com.riaride.payments.internal.ledger;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.util.UUID;
import org.springframework.data.domain.Persistable;

// One charge or refund at the payment provider.
@Entity
@Table(name = "payment")
class Payment implements Persistable<UUID> {

  enum Kind {
    PURCHASE,
    OVERTIME
  }

  enum Status {
    SUCCEEDED,
    // Refused for good by the provider: there is no payment to refer to.
    FAILED
  }

  @Id private UUID id;

  @Column(name = "pass_id", nullable = false, updatable = false)
  private UUID passId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, updatable = false)
  private Kind kind;

  @Column(name = "amount_cents", nullable = false, updatable = false)
  private long amountCents;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private Status status;

  @Column(name = "provider_ref")
  private String providerRef;

  @Column(name = "idempotency_key", nullable = false, updatable = false)
  private String idempotencyKey;

  @Column(name = "customer_ref")
  private String customerRef;

  @Column(name = "payment_method_ref")
  private String paymentMethodRef;

  @Transient private boolean isNew;

  protected Payment() {
    // for JPA
  }

  private Payment(
      UUID passId,
      Kind kind,
      long amountCents,
      String providerRef,
      String idempotencyKey,
      String customerRef,
      String paymentMethodRef,
      Status status) {
    this.id = UUID.randomUUID();
    this.passId = passId;
    this.kind = kind;
    this.amountCents = amountCents;
    this.status = status;
    this.providerRef = providerRef;
    this.idempotencyKey = idempotencyKey;
    this.customerRef = customerRef;
    this.paymentMethodRef = paymentMethodRef;
    this.isNew = true;
  }

  static Payment purchase(
      UUID passId,
      long amountCents,
      String providerRef,
      String idempotencyKey,
      String customerRef,
      String paymentMethodRef) {
    return new Payment(
        passId,
        Kind.PURCHASE,
        amountCents,
        providerRef,
        idempotencyKey,
        customerRef,
        paymentMethodRef,
        Status.SUCCEEDED);
  }

  // An overtime charge made off-session with the purchase's saved card (RIA-88).
  static Payment overtime(
      UUID passId, long amountCents, String providerRef, String idempotencyKey) {
    return new Payment(
        passId,
        Kind.OVERTIME,
        amountCents,
        providerRef,
        idempotencyKey,
        null,
        null,
        Status.SUCCEEDED);
  }

  // An overtime charge the provider refused for good; it is not tried again (RIA-88).
  static Payment failedOvertime(UUID passId, long amountCents, String idempotencyKey) {
    return new Payment(
        passId, Kind.OVERTIME, amountCents, null, idempotencyKey, null, null, Status.FAILED);
  }

  @Override
  public UUID getId() {
    return id;
  }

  @Override
  public boolean isNew() {
    return isNew;
  }

  @PostLoad
  @PostPersist
  void markNotNew() {
    this.isNew = false;
  }

  UUID getPassId() {
    return passId;
  }

  Kind getKind() {
    return kind;
  }

  String getProviderRef() {
    return providerRef;
  }

  String getIdempotencyKey() {
    return idempotencyKey;
  }

  Status getStatus() {
    return status;
  }

  long getAmountCents() {
    return amountCents;
  }

  String getCustomerRef() {
    return customerRef;
  }

  String getPaymentMethodRef() {
    return paymentMethodRef;
  }
}
