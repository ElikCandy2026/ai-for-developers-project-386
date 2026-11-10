# AGENTS

## Commands

Run everything from the repository root. The `make` targets are the source of
truth; CI runs `make install && make verify`.

| Command | Purpose |
| --- | --- |
| `make install` | Install `contract` and `frontend` dependencies (`npm ci`) and the Playwright browser |
| `make run` | Build the project and start it at <http://localhost:8080> |
| `make dev-backend` | Run the Spring Boot backend without rebuilding the frontend |
| `make dev-frontend` | Run the Vite dev server for the frontend |
| `make test` | Backend tests (JUnit and ArchUnit) |
| `make frontend-test` | Frontend tests (vitest) |
| `make lint` | Static checks: ESLint and TypeScript (`tsc --noEmit`) |
| `make e2e` | Playwright browser test against the packaged app on a clean database |
| `make e2e-install` | Download the Playwright Chromium browser |
| `make verify` | Full pre-commit gate: generated files, lint, frontend build and tests, browser e2e, backend tests, boundary |

`make generate` regenerates OpenAPI and the TypeScript SDK from `contract/main.tsp`;
`make check-generated` fails when the committed generated files are stale.

## Agent skills

### Issue tracker

Issues live in GitHub Issues at `ElikCandy2026/ai-for-developers-project-386`. See `docs/agents/issue-tracker.md`.

### Triage labels

Default canonical labels: `needs-triage`, `needs-info`, `ready-for-agent`, `ready-for-human`, `wontfix`. See `docs/agents/triage-labels.md`.

### Commits

New commits follow [Conventional Commits](https://www.conventionalcommits.org/)
and reference the ticket they serve:

```
<type>: <short imperative description> (#<n>)
```

Example: `feat: add home page test (#17)`.

Types: `feat`, `fix`, `docs`, `refactor`, `test`, `chore`, `ci`, `build`, `perf`.

Add `Refs #<n>` in the body while the work is in progress and `Closes #<n>` when
the commit finishes the ticket. Work that belongs to a wayfinder map rather than a
single ticket uses `Part of #<n>`. Commits without a ticket reference are not
accepted. Old history is not rewritten.

### Domain docs

Single-context: `GLOSSARY.md` and `docs/adr/` at the repo root. See `docs/agents/domain.md`.
