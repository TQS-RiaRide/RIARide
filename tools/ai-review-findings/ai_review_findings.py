"""Counts the AI review findings recorded in pull request bodies (QA Manual, section 6.7)."""

from __future__ import annotations

import re
from typing import NamedTuple

SEVERITIES = ("CRITICAL", "HIGH", "MEDIUM", "LOW")
UNRATED = "UNRATED"
DECISIONS = ("ACCEPTED", "PARTIAL", "REJECTED", "DEFERRED")
UNKNOWN = "UNKNOWN"

_EMPHASIS = re.compile(r"[*_`]")
_SEVERITY = re.compile(rf"^({'|'.join(SEVERITIES)})\s*:", re.IGNORECASE)
_PARTIAL = ("accepted in part", "partly accepted", "partially accepted")
_DECISION_WORDS = (("accepted", "ACCEPTED"), ("rejected", "REJECTED"), ("deferred", "DEFERRED"))


def _plain(cell: str) -> str:
    return _EMPHASIS.sub("", cell).strip()


def parse_severity(finding: str) -> str:
    """Severity at the start of a finding ("HIGH: ..."), or UNRATED when it has none."""
    match = _SEVERITY.match(_plain(finding))
    return match.group(1).upper() if match else UNRATED


def parse_decision(decision: str) -> str:
    """Decision of a finding, ignoring case, emphasis and trailing notes in parentheses."""
    text = _plain(decision).lower()
    if text.startswith(_PARTIAL):
        return "PARTIAL"
    for word, result in _DECISION_WORDS:
        if text.startswith(word):
            return result
    return UNKNOWN


class Finding(NamedTuple):
    severity: str
    decision: str
    text: str


def extract_findings(body: str | None) -> list[Finding]:
    raise NotImplementedError
