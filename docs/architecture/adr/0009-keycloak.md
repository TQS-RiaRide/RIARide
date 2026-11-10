# ADR 0009: Keycloak for authentication

**Status:** proposed  
**Date:** 2026-11-10

## Context

The platform has four roles and needs login, password handling and role management.

## Decision

Use Keycloak with OIDC. The SPA uses Authorization Code with PKCE, the backend is an OAuth2 resource server, and the realm is exported to the repository. Keycloak uses its own database on the shared PostgreSQL server.

## Alternatives considered

- Spring Security session cookie: fewer parts and immediate revocation, but password storage and account flows become our code.
- Self-issued JWT: no server state, but revocation, refresh and storage are left to us.

## Consequences

- Credentials and login pages stay out of our code.
- Adds a memory-hungry container to CI and deployment.
- Requires a custom registration path, covered by ADR 0010.
