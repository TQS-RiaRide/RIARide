# ADR 0013: One base price across the hotel chain

**Status:** proposed  
**Date:** 2026-11-10

## Context

Guests can use bikes at any hub, so per-hotel prices would let a guest pay one hotel's price and ride from another. The team document now states one base price for all hotels.

## Decision

Store one row per pass type (1-day, 3-day, stay) in `pass_price`, with no hotel reference. A pass copies its price and deposit at purchase.

## Alternatives considered

- Chain default with hotel override: more flexible, but reintroduces the inconsistency.
- Per-hotel price: contradicts the team's updated rule.

## Consequences

- Simpler data model and backoffice.
- Past passes keep the price they were sold at.
- Epic 8 in the team document needs updating.
