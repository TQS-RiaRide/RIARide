import pytest

from ai_review_findings import parse_decision, parse_severity


@pytest.mark.parametrize(
    ("finding", "expected"),
    [
        ("CRITICAL: token logged", "CRITICAL"),
        ("HIGH: lost update on vehicle.status", "HIGH"),
        ("MEDIUM: missing index", "MEDIUM"),
        ("LOW: rename variable", "LOW"),
    ],
)
def test_reads_severity_at_start_of_finding(finding: str, expected: str) -> None:
    assert parse_severity(finding) == expected


@pytest.mark.parametrize(
    "finding",
    ["high: lower case", "**HIGH**: bold", "`HIGH`: code", "  HIGH : spaces around colon"],
)
def test_reads_severity_ignoring_case_and_markdown_emphasis(finding: str) -> None:
    assert parse_severity(finding) == "HIGH"


@pytest.mark.parametrize(
    "finding",
    [
        "An older reading could overwrite a newer one",
        "Security: the broker connection is unauthenticated",
        "Highlight the row",
        "HIGH without a colon",
        "",
    ],
)
def test_reports_finding_without_leading_severity_as_unrated(finding: str) -> None:
    assert parse_severity(finding) == "UNRATED"


@pytest.mark.parametrize(
    ("decision", "expected"),
    [
        ("Accepted", "ACCEPTED"),
        ("accepted", "ACCEPTED"),
        ("Accepted (documented)", "ACCEPTED"),
        ("**Accepted**", "ACCEPTED"),
        ("Accepted in part", "PARTIAL"),
        ("Partly accepted", "PARTIAL"),
        ("Rejected", "REJECTED"),
        ("Rejected (false positive)", "REJECTED"),
        ("Deferred", "DEFERRED"),
    ],
)
def test_normalizes_decision(decision: str, expected: str) -> None:
    assert parse_decision(decision) == expected


@pytest.mark.parametrize("decision", ["", "Maybe", "Not accepted yet"])
def test_reports_unrecognized_decision_as_unknown(decision: str) -> None:
    assert parse_decision(decision) == "UNKNOWN"
