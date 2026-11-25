"""Counts the AI review findings recorded in pull request bodies (QA Manual, section 6.7)."""

from __future__ import annotations


def parse_severity(finding: str) -> str:
    raise NotImplementedError


def parse_decision(decision: str) -> str:
    raise NotImplementedError
