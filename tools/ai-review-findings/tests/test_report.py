import csv
import io
import json
import subprocess
from collections.abc import Sequence

import pytest

from ai_review_findings import ai_label, fetch_pull_requests, main, tally

BODY_TWO_FINDINGS = """### AI review findings

| Finding | Accepted / Rejected | Reason |
|---|---|---|
| HIGH: lost update | Accepted | Fixed |
| LOW: rename | Rejected | Domain name |
"""

BODY_ONE_FINDING = """### AI review findings

| Finding | Accepted / Rejected | Reason |
|---|---|---|
| HIGH: missing check | Accepted in part | Partly fixed |
"""

PULL_REQUESTS = [
    {"number": 40, "labels": [{"name": "ai:assisted"}], "body": BODY_TWO_FINDINGS},
    {"number": 41, "labels": [{"name": "e2e"}, {"name": "ai:none"}], "body": BODY_ONE_FINDING},
    {"number": 42, "labels": [], "body": None},
]


class FakeGh:
    def __init__(self, stdout: str = "[]", returncode: int = 0, stderr: str = "") -> None:
        self.stdout = stdout
        self.returncode = returncode
        self.stderr = stderr
        self.args: Sequence[str] = ()

    def __call__(self, args: Sequence[str], **_: object) -> subprocess.CompletedProcess[str]:
        self.args = args
        return subprocess.CompletedProcess(args, self.returncode, self.stdout, self.stderr)


def test_reads_ai_label_or_reports_unlabelled() -> None:
    assert [ai_label(pr) for pr in PULL_REQUESTS] == ["ai:assisted", "ai:none", "unlabelled"]


def test_counts_findings_by_severity_and_decision() -> None:
    counts = tally(PULL_REQUESTS)

    assert counts == {
        ("HIGH", "ACCEPTED"): 1,
        ("LOW", "REJECTED"): 1,
        ("HIGH", "PARTIAL"): 1,
    }


def test_fetches_pull_requests_with_their_bodies_in_one_gh_call() -> None:
    gh = FakeGh(stdout=json.dumps(PULL_REQUESTS))

    prs = fetch_pull_requests(state="merged", base="dev", limit=50, run=gh)

    assert prs == PULL_REQUESTS
    assert list(gh.args) == [
        "gh",
        "pr",
        "list",
        "--state",
        "merged",
        "--limit",
        "50",
        "--json",
        "number,title,labels,body",
        "--base",
        "dev",
    ]


def test_fetches_from_every_base_branch_when_none_is_given() -> None:
    gh = FakeGh()

    fetch_pull_requests(state="all", base=None, limit=10, run=gh)

    assert "--base" not in gh.args


def test_fails_with_gh_error_message_when_gh_fails() -> None:
    gh = FakeGh(returncode=1, stderr="gh: not logged in")

    with pytest.raises(SystemExit, match="not logged in"):
        fetch_pull_requests(state="merged", base=None, limit=10, run=gh)


def test_fails_with_clear_message_when_gh_is_not_installed() -> None:
    def missing_gh(args: Sequence[str], **_: object) -> subprocess.CompletedProcess[str]:
        raise FileNotFoundError("gh")

    with pytest.raises(SystemExit, match="GitHub CLI"):
        fetch_pull_requests(state="merged", base=None, limit=10, run=missing_gh)


def test_prints_table_of_counts_with_totals() -> None:
    out = io.StringIO()

    exit_code = main([], run=FakeGh(stdout=json.dumps(PULL_REQUESTS)), out=out)

    assert exit_code == 0
    lines = out.getvalue().splitlines()
    assert lines[0].split() == [
        "Severity",
        "ACCEPTED",
        "PARTIAL",
        "REJECTED",
        "DEFERRED",
        "UNKNOWN",
        "Total",
    ]
    assert lines[1].split() == ["CRITICAL", "0", "0", "0", "0", "0", "0"]
    assert lines[2].split() == ["HIGH", "1", "1", "0", "0", "0", "2"]
    assert lines[4].split() == ["LOW", "0", "0", "1", "0", "0", "1"]
    assert lines[5].split() == ["UNRATED", "0", "0", "0", "0", "0", "0"]
    assert lines[6].split() == ["Total", "1", "1", "1", "0", "0", "3"]
    assert lines[-1] == "3 findings in 2 of 3 pull requests"


def test_prints_one_csv_row_per_finding() -> None:
    out = io.StringIO()

    main(["--format", "csv"], run=FakeGh(stdout=json.dumps(PULL_REQUESTS)), out=out)

    rows = list(csv.DictReader(io.StringIO(out.getvalue())))
    assert [(r["pr"], r["ai_label"], r["severity"], r["decision"]) for r in rows] == [
        ("40", "ai:assisted", "HIGH", "ACCEPTED"),
        ("40", "ai:assisted", "LOW", "REJECTED"),
        ("41", "ai:none", "HIGH", "PARTIAL"),
    ]
    assert rows[0]["finding"] == "HIGH: lost update"


def test_prints_counts_per_ai_label_as_json() -> None:
    out = io.StringIO()

    main(["--format", "json"], run=FakeGh(stdout=json.dumps(PULL_REQUESTS)), out=out)

    report = json.loads(out.getvalue())
    assert report["pull_requests"] == 3
    assert report["pull_requests_with_findings"] == 2
    assert report["total"]["HIGH"] == {"ACCEPTED": 1, "PARTIAL": 1}
    assert report["by_ai_label"]["ai:none"] == {"HIGH": {"PARTIAL": 1}}
    assert "unlabelled" not in report["by_ai_label"]


def test_passes_state_base_and_limit_to_gh() -> None:
    gh = FakeGh()

    main(["--state", "all", "--base", "main", "--limit", "5"], run=gh, out=io.StringIO())

    assert gh.args[gh.args.index("--state") + 1] == "all"
    assert gh.args[gh.args.index("--base") + 1] == "main"
    assert gh.args[gh.args.index("--limit") + 1] == "5"
