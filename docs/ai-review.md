# AI-assisted code review

Practice AI3. Every non-draft pull request gets an advisory review from Claude.

## How it works

- Workflow: [`.github/workflows/ai-review.yml`](../.github/workflows/ai-review.yml), using `anthropics/claude-code-action`.
- Triggers: PR opened, reopened or marked ready for review. Pushes do not trigger a new review, to avoid duplicate findings; use **Re-run jobs** on the workflow run to request one after changes. Drafts, PRs from forks and Dependabot PRs are skipped (they have no access to secrets).
- Output: inline comments and one summary comment, each finding tagged `CRITICAL`, `HIGH`, `MEDIUM` or `LOW`.
- `id-token: write` is required: the action exchanges an OIDC token for a Claude GitHub App installation token. That token has the **app's** permissions (including contents write), not the job's `contents: read`. What actually limits the bot is `--allowedTools` in the workflow: read-only tools plus `gh pr comment`, `gh pr diff` and `gh pr view` scoped to the current PR number. Never add `Edit`, `Write`, `Bash(git:*)` or unscoped `gh` commands. The bot never approves, requests changes or merges.
- Runs for the same PR queue instead of cancelling each other, so a cancelled run cannot leave orphaned inline comments.

## Setup (once, repository admin)

1. Install the Claude GitHub App on the repository (run `/install-github-app` in Claude Code, or install it from https://github.com/apps/claude). Without it the token exchange fails and no review is posted.
2. Generate a token with `claude setup-token` and add it as the repository secret `CLAUDE_CODE_OAUTH_TOKEN` (Settings, Secrets and variables, Actions).
3. Keep branch protection on `main`: at least one human approval, no bypass for the bot.

### Fallback if the token stops working

`CLAUDE_CODE_OAUTH_TOKEN` belongs to one member's personal Claude Pro/Max subscription. If that member hits their usage limit or revokes the token, AI review stops on every PR. To recover, either:

- another member generates a new token with `claude setup-token` and replaces the secret, or
- add an Anthropic API key as the secret `ANTHROPIC_API_KEY` and, in the workflow, replace `claude_code_oauth_token: ...` with `anthropic_api_key: ${{ secrets.ANTHROPIC_API_KEY }}`.

Never send secrets, credentials or personal data in PRs. Use synthetic data only.

## Recording findings

The PR author fills the **AI review findings** table in the PR template for each finding:

| Column | Content |
|---|---|
| Finding | Starts with the severity and a colon (`CRITICAL:`, `HIGH:`, `MEDIUM:` or `LOW:`), then a short quote or link to the comment |
| Accepted / Rejected | `Accepted`, `Accepted in part`, `Rejected` or `Deferred`; a note may follow in parentheses |
| Reason | Why. For rejected findings, say why the AI was wrong or why it does not apply |

For example: `| HIGH: lost update on vehicle.status | Accepted | @Version and an IT first |`. Findings from other AI reviewers (a local agent, Copilot) go in the same table. A row with neither a severity nor a decision, such as `No AI review findings | Not applicable`, is a note and is not counted.

The reviewing human checks the table before approving. These tables are the evidence for the QA Manual compliance row and the critical AI analysis report.

## Counting findings

[`tools/ai-review-findings`](../tools/ai-review-findings/README.md) reads the tables of every pull request with the GitHub CLI and counts the findings by severity and decision, in total and per `ai:*` label:

```bash
python tools/ai-review-findings/ai_review_findings.py --state merged --base dev
```

Rows written before the severity convention are counted as `UNRATED`.

## Limits

- The AI review does not replace the human review or the SonarQube quality gate.
- Findings can be wrong or miss bugs. Any member may have to explain any merged code.
