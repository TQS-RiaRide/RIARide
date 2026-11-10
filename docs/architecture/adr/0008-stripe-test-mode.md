# ADR 0008: Stripe test mode behind a port

**Status:** proposed  
**Date:** 2026-11-10

## Context

The guest pays for a pass and a deposit, gets the deposit refunded later, and may be charged overtime when no longer present. Payments must sit behind an adapter with integration tests.

## Decision

Implement `PaymentGateway` with Stripe test mode. The backend confirms PaymentIntents with test payment methods and sends an idempotency key per operation. The purchase saves the payment method with `setup_future_usage=off_session`, and overtime is charged off-session to it; the adapter finds it through the purchase payment's `provider_ref`. Integration tests use WireMock.

## Alternatives considered

- Simulated gateway: allowed and deterministic, but its adapter test proves little.
- Stripe Checkout with webhooks: more realistic, but needs a public webhook endpoint and complicates off-session charges.

## Consequences

- The adapter performs a real integration without real money.
- CI needs no network access to Stripe.
- WireMock stubs can drift from Stripe's real API.
- The MVP does not collect real card details.
