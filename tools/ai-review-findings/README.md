# AI review findings

Counts the AI review findings that authors record in the *AI review findings* table of each pull request, by severity and decision (QA Manual, section 6.7; practice AI3). The counts feed the critical analysis of AI's contribution.

## Row format

```markdown
| Finding | Accepted / Rejected | Reason |
|---|---|---|
| HIGH: lost update on `vehicle.status` | Accepted | `@Version`; IT first |
| LOW: rename `x` | Rejected | The name follows the domain |
```

- **Severity**, at the start of the finding: `CRITICAL`, `HIGH`, `MEDIUM` or `LOW`, followed by a colon. A finding without one is counted as `UNRATED` (rows written before this convention).
- **Decision**: `Accepted`, `Accepted in part`, `Rejected` or `Deferred`, optionally followed by a note in parentheses. Anything else is `UNKNOWN`.
- A row with neither a severity nor a decision (`No AI review findings | Not applicable`) is a note and is not counted.

## Usage

Needs Python 3.12 or later and the [GitHub CLI](https://cli.github.com/), logged in. From the repository root:

```bash
python tools/ai-review-findings/ai_review_findings.py --state merged --base dev
```

| Option | Default | Meaning |
|---|---|---|
| `--state` | `merged` | `open`, `closed`, `merged` or `all` |
| `--base` | any | Only pull requests into this branch |
| `--limit` | `500` | Most recent pull requests to read |
| `--format` | `table` | `table` (counts), `csv` (one row per finding) or `json` (counts in total and per `ai:*` label) |

The script makes one `gh pr list` call and needs no other access.

## Development

From this directory, in a virtual environment:

```bash
pip install -r requirements-dev.txt
ruff check . && ruff format --check .
python -m pytest --cov
```

After changing `requirements-dev.in`, regenerate the hashed file:

```bash
pip-compile --generate-hashes --allow-unsafe -o requirements-dev.txt requirements-dev.in
```
