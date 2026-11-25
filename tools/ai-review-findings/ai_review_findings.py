"""Counts the AI review findings recorded in pull request bodies (QA Manual, section 6.7)."""

from __future__ import annotations

import argparse
import csv
import json
import re
import subprocess
import sys
from collections import Counter
from collections.abc import Callable
from typing import Any, NamedTuple, TextIO

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


PullRequest = dict[str, Any]
Counts = dict[tuple[str, str], int]
Runner = Callable[..., subprocess.CompletedProcess[str]]

GH_FIELDS = "number,title,labels,body"
UNLABELLED = "unlabelled"
SEVERITY_ROWS = (*SEVERITIES, UNRATED)
DECISION_COLUMNS = (*DECISIONS, UNKNOWN)


def ai_label(pull_request: PullRequest) -> str:
    """The pull request's ai:* label (QA Manual 6.7), or "unlabelled"."""
    names = [label.get("name", "") for label in pull_request.get("labels") or []]
    return next((name for name in names if name.startswith("ai:")), UNLABELLED)


def tally(pull_requests: list[PullRequest]) -> Counts:
    """Number of findings per (severity, decision) over all pull requests."""
    counts: Counter[tuple[str, str]] = Counter(
        (finding.severity, finding.decision)
        for pull_request in pull_requests
        for finding in extract_findings(pull_request.get("body"))
    )
    return dict(counts)


def fetch_pull_requests(
    state: str, base: str | None, limit: int, run: Runner = subprocess.run
) -> list[PullRequest]:
    """Pull requests with their bodies, from one `gh pr list` call."""
    args = ["gh", "pr", "list", "--state", state, "--limit", str(limit), "--json", GH_FIELDS]
    if base:
        args += ["--base", base]
    try:
        result = run(args, capture_output=True, text=True, check=False)
    except FileNotFoundError:
        raise SystemExit("The GitHub CLI (gh) is not installed or not on PATH.") from None
    if result.returncode != 0:
        raise SystemExit(f"gh pr list failed: {result.stderr.strip()}")
    return json.loads(result.stdout)


def _count_with_findings(pull_requests: list[PullRequest]) -> int:
    return sum(1 for pr in pull_requests if extract_findings(pr.get("body")))


def _nested(counts: Counts) -> dict[str, dict[str, int]]:
    nested: dict[str, dict[str, int]] = {}
    for (severity, decision), count in sorted(counts.items()):
        nested.setdefault(severity, {})[decision] = count
    return nested


def _table_rows(counts: Counts) -> list[list[str]]:
    rows = [["Severity", *DECISION_COLUMNS, "Total"]]
    for severity in SEVERITY_ROWS:
        values = [counts.get((severity, decision), 0) for decision in DECISION_COLUMNS]
        rows.append([severity, *map(str, values), str(sum(values))])
    totals = [sum(counts.get((s, d), 0) for s in SEVERITY_ROWS) for d in DECISION_COLUMNS]
    rows.append(["Total", *map(str, totals), str(sum(totals))])
    return rows


def _write_table(pull_requests: list[PullRequest], out: TextIO) -> None:
    counts = tally(pull_requests)
    rows = _table_rows(counts)
    widths = [max(len(row[i]) for row in rows) for i in range(len(rows[0]))]
    for row in rows:
        cells = (cell.ljust(width) for cell, width in zip(row, widths, strict=True))
        out.write("  ".join(cells).rstrip() + "\n")
    total = sum(counts.values())
    with_findings = _count_with_findings(pull_requests)
    out.write(f"\n{total} findings in {with_findings} of {len(pull_requests)} pull requests\n")


def _write_csv(pull_requests: list[PullRequest], out: TextIO) -> None:
    writer = csv.writer(out, lineterminator="\n")
    writer.writerow(["pr", "ai_label", "severity", "decision", "finding"])
    for pull_request in pull_requests:
        label = ai_label(pull_request)
        for finding in extract_findings(pull_request.get("body")):
            writer.writerow(
                [
                    pull_request.get("number"),
                    label,
                    finding.severity,
                    finding.decision,
                    finding.text,
                ]
            )


def _write_json(pull_requests: list[PullRequest], out: TextIO) -> None:
    by_label: dict[str, list[PullRequest]] = {}
    for pull_request in pull_requests:
        by_label.setdefault(ai_label(pull_request), []).append(pull_request)
    label_counts = {label: tally(prs) for label, prs in sorted(by_label.items())}
    report = {
        "pull_requests": len(pull_requests),
        "pull_requests_with_findings": _count_with_findings(pull_requests),
        "total": _nested(tally(pull_requests)),
        "by_ai_label": {label: _nested(c) for label, c in label_counts.items() if c},
    }
    json.dump(report, out, indent=2)
    out.write("\n")


WRITERS: dict[str, Callable[[list[PullRequest], TextIO], None]] = {
    "table": _write_table,
    "csv": _write_csv,
    "json": _write_json,
}


def _parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description="Count the AI review findings recorded in pull request bodies, "
        "by severity and decision (QA Manual, section 6.7)."
    )
    parser.add_argument("--state", default="merged", choices=["open", "closed", "merged", "all"])
    parser.add_argument("--base", help="only pull requests into this branch, for example dev")
    parser.add_argument("--limit", type=int, default=500, help="most recent N (default 500)")
    parser.add_argument("--format", default="table", choices=sorted(WRITERS))
    return parser


def main(
    argv: list[str] | None = None, run: Runner = subprocess.run, out: TextIO = sys.stdout
) -> int:
    args = _parser().parse_args(argv)
    pull_requests = fetch_pull_requests(args.state, args.base, args.limit, run)
    WRITERS[args.format](pull_requests, out)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
