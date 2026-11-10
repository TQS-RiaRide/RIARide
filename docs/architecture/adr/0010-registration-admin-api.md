# ADR 0010: Guest registration through the Keycloak Admin API

**Status:** proposed  
**Date:** 2026-11-10

## Context

Only guests with an active stay can register, using an email, a password and a stay code. Keycloak's standard registration does not know about stays.

## Decision

The SPA posts the form to the backend. `accounts` validates the stay, creates the user and GUEST role through the Admin API with a service client, then stores the guest. If that insert fails, it deletes the Keycloak user.

## Alternatives considered

- Custom Keycloak SPI: native form, but business logic moves into a JAR inside Keycloak, away from our coverage and tied to its version.
- Open registration with the code entered later: creates accounts for non-guests, contradicting the access rule.

## Consequences

- The access rule stays in tested application code.
- The backend briefly handles the password and must never store or log it.
- A crash between the two steps can leave an orphan Keycloak user, so a registration that finds an existing user with no local guest must reuse that user.
