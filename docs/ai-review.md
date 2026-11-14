# AI-assisted code review

Practice AI3. Every non-draft pull request gets an advisory review from Claude.

## How it works

- Workflow: [`.github/workflows/ai-review.yml`](../.github/workflows/ai-review.yml), using `anthropics/claude-code-action`.
- Triggers: PR opened, updated, reopened or marked ready for review. Drafts and PRs from forks are skipped.
- Output: inline comments and one summary comment, each finding tagged `CRITICAL`, `HIGH`, `MEDIUM` or `LOW`.
- The job has `contents: read` and can only comment. It never approves, requests changes or merges.
- A new push cancels a review still running for the same PR.

## Setup (once, repository admin)

1. Generate a token with `claude setup-token` and add it as the repository secret `CLAUDE_CODE_OAUTH_TOKEN` (Settings, Secrets and variables, Actions). To use an API key instead, set `anthropic_api_key: ${{ secrets.ANTHROPIC_API_KEY }}` in the workflow.
2. Keep branch protection on `main`: at least one human approval, no bypass for the bot.

Never send secrets, credentials or personal data in PRs. Use synthetic data only.

## Recording findings

The PR author fills the **AI review findings** table in the PR template for each finding:

| Column | Content |
|---|---|
| Finding | Short quote or link to the comment, with its severity |
| Accepted / Rejected | Decision |
| Reason | Why. For rejected findings, say why the AI was wrong or why it does not apply |

The reviewing human checks the table before approving. These tables are the evidence for the QA Manual compliance row and the critical AI analysis report.

## Limits

- The AI review does not replace the human review or the SonarQube quality gate.
- Findings can be wrong or miss bugs. Any member may have to explain any merged code.
