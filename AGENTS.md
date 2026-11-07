# AGENTS

## Agent skills

### Issue tracker

Issues live in GitHub Issues at `ElikCandy2026/ai-for-developers-project-386`. See `docs/agents/issue-tracker.md`.

### Triage labels

Default canonical labels: `needs-triage`, `needs-info`, `ready-for-agent`, `ready-for-human`, `wontfix`. See `docs/agents/triage-labels.md`.

### Commits

Every commit references the issue it serves: `Refs #<n>` while the work is in
progress, `Closes #<n>` when the commit finishes the ticket. Work that belongs to a
wayfinder map rather than a single ticket uses `Part of #<n>`. Commits without a
ticket reference are not accepted.

### Domain docs

Single-context: `GLOSSARY.md` and `docs/adr/` at the repo root. See `docs/agents/domain.md`.
