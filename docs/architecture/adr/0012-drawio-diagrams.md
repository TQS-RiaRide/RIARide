# ADR 0012: draw.io diagrams stored as SVG

**Status:** proposed  
**Date:** 2026-11-10

## Context

The proposal needs context, container, module, data, sequence, state and deployment diagrams, reviewed alongside the code.

## Decision

Author diagrams in draw.io and store them as `.drawio.svg`, with the source embedded. GitHub renders them, and draw.io edits the same file.

## Alternatives considered

- Mermaid: text diffs, but limited layout control.
- Structurizr DSL: consistent C4 views, but C4 only and one more tool.
- PlantUML: mature, but GitHub does not render it directly.

## Consequences

- Full layout control and one file per diagram.
- Pull requests show images, not meaningful text diffs.
- Reviewers must check that diagrams still match the text.
