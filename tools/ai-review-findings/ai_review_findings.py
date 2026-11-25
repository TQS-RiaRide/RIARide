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


_SECTION = re.compile(r"^#+\s*AI review findings\s*$", re.IGNORECASE)
_HEADING = re.compile(r"^#+\s")
_SEPARATOR_CELL = re.compile(r"^:?-+:?$")
_UNESCAPED_PIPE = re.compile(r"(?<!\\)\|")


def _section_lines(body: str) -> list[str]:
    lines = [line.strip() for line in body.splitlines()]
    start = next((i for i, line in enumerate(lines) if _SECTION.match(line)), None)
    if start is None:
        return []
    section = []
    for line in lines[start + 1 :]:
        if _HEADING.match(line):
            break
        section.append(line)
    return section


def _cells(row: str) -> list[str]:
    inner = row.strip().removeprefix("|").removesuffix("|")
    return [cell.replace("\\|", "|").strip() for cell in _UNESCAPED_PIPE.split(inner)]


def _is_finding_row(cells: list[str]) -> bool:
    if not any(cells):
        return False
    if all(_SEPARATOR_CELL.match(cell) for cell in cells):
        return False
    return _plain(cells[0]).lower() != "finding"


def extract_findings(body: str | None) -> list[Finding]:
    """Findings in the body's "AI review findings" table; none if the section is missing."""
    if not body:
        return []
    findings = []
    for line in _section_lines(body):
        if not line.startswith("|"):
            continue
        cells = _cells(line)
        if not _is_finding_row(cells):
            continue
        text = cells[0]
        decision = cells[1] if len(cells) > 1 else ""
        findings.append(Finding(parse_severity(text), parse_decision(decision), text))
    return findings
