from ai_review_findings import Finding, extract_findings

TEMPLATE_BODY = """## AI use

### AI review findings

<!--
One row per finding.
-->

| Finding | Accepted / Rejected | Reason |
|---|---|---|
|  |  |  |

## Checklist
"""


def test_returns_no_findings_for_untouched_template() -> None:
    assert extract_findings(TEMPLATE_BODY) == []


def test_returns_no_findings_when_section_is_missing() -> None:
    body = "## Story\n\nRIA-1\n\n| a | b | c |\n|---|---|---|\n| x | y | z |\n"

    assert extract_findings(body) == []


def test_returns_no_findings_for_empty_body() -> None:
    assert extract_findings(None) == []
    assert extract_findings("") == []


def test_reads_each_row_with_severity_and_decision() -> None:
    body = """### AI review findings

| Finding | Accepted / Rejected | Reason |
|---|---|---|
| HIGH: lost update on `vehicle.status` | Accepted | `@Version`; IT first |
| LOW: rename `x` | Rejected | Name follows the domain |

## Checklist
"""

    findings = extract_findings(body)

    assert findings == [
        Finding("HIGH", "ACCEPTED", "HIGH: lost update on `vehicle.status`"),
        Finding("LOW", "REJECTED", "LOW: rename `x`"),
    ]


def test_reads_rows_written_before_the_severity_convention_as_unrated() -> None:
    body = """### AI review findings

Pre-PR review by a Claude Code agent:

| Finding | Accepted / Rejected | Reason |
|---|---|---|
| Security: the broker connection is unauthenticated | Accepted in part | Optional user |
| `Vehicle.withStatus` is a test-only factory | Deferred | Removed later |
"""

    findings = extract_findings(body)

    assert [(f.severity, f.decision) for f in findings] == [
        ("UNRATED", "PARTIAL"),
        ("UNRATED", "DEFERRED"),
    ]


def test_handles_windows_line_endings_and_escaped_pipes() -> None:
    body = (
        "### AI review findings\r\n\r\n"
        "| Finding | Accepted / Rejected | Reason |\r\n"
        "|---|---|---|\r\n"
        "| MEDIUM: `a \\| b` is ambiguous | Accepted | Parenthesized |\r\n"
    )

    findings = extract_findings(body)

    assert findings == [Finding("MEDIUM", "ACCEPTED", "MEDIUM: `a | b` is ambiguous")]


def test_stops_at_the_next_heading() -> None:
    body = """### AI review findings

| Finding | Accepted / Rejected | Reason |
|---|---|---|
| HIGH: real finding | Accepted | Fixed |

## Checklist

| Finding | Accepted / Rejected | Reason |
|---|---|---|
| LOW: not a finding | Rejected | Other table |
"""

    assert len(extract_findings(body)) == 1


def test_reads_row_with_missing_decision_as_unknown() -> None:
    body = "### AI review findings\n\n| Finding | Decision | Reason |\n|---|---|---|\n| LOW: x |\n"

    assert extract_findings(body) == [Finding("LOW", "UNKNOWN", "LOW: x")]


def test_skips_note_rows_with_neither_severity_nor_decision() -> None:
    body = """### AI review findings

| Finding | Accepted / Rejected | Reason |
|---|---|---|
| No AI review findings | Not applicable | |
| Copilot: no review (quota) |  |  |
| Pending AI review run | n/a | |
| LOW: real one | Accepted | Fixed |
"""

    assert extract_findings(body) == [Finding("LOW", "ACCEPTED", "LOW: real one")]
