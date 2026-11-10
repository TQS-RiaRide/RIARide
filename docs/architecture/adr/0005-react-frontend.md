# ADR 0005: React, Vite and TypeScript

**Status:** proposed  
**Date:** 2026-11-10

## Context

A responsive web UI must serve four roles with dashboards, in Portuguese and English.

## Decision

Build one SPA with React, Vite and TypeScript, using react-i18next for translations, Vitest for unit tests, and an API client generated from OpenAPI.

## Alternatives considered

- Angular: complete framework, but heavier to start.
- Vue: gentle learning curve, smaller ecosystem.
- Thymeleaf with htmx: one deployable, but rich dashboards are harder.

## Consequences

- Strong typing across the API boundary.
- The team must choose routing and data-fetching libraries in the walking skeleton.
