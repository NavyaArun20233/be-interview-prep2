# Code review guide

Used for self-review before every push and for reviewing pull requests. Coding and
architecture standards are in [../CLAUDE.md](../CLAUDE.md); this document defines *how*
to review against them.

## Procedure

1. `git fetch origin` then collect the full change set:
   - self-review: `git status`, `git diff origin/main...HEAD`, `git diff`, `git diff --cached`
   - PR review: `gh pr diff <n>` and `gh pr view <n>`
2. Read **every** changed file in full context (not just the hunk) — callers, tests, and
   the migration history where relevant.
3. Walk the checklist below. Record each finding with a severity, `file:line`, the
   concrete failure scenario, and a suggested fix.
4. Run `./mvnw -B -ntp verify`. A failing gate is at least P1.
5. Fix all P0–P2 findings (or get an explicit decision to defer, recorded in the PR).
   P3 is optional.

## Severity

| Level | Meaning | Examples | Blocks merge |
|-------|---------|----------|--------------|
| **P0 — Critical** | Security, data loss, outage | SQL injection, auth bypass, secret committed, destructive migration without plan, data deleted/corrupted, app fails to start | Yes |
| **P1 — High** | Wrong behavior or serious risk | functional bug, wrong business rule, broken API contract, missing transaction causing partial writes, N+1 on a list endpoint, race condition, failing/flaky test | Yes |
| **P2 — Medium** | Maintainability / robustness gaps | missing validation, missing tests for new behavior, swallowed exception, layer violation, missing index for new query, unclear error response | Yes, unless deferral is explicitly agreed |
| **P3 — Low** | Polish | naming, comments, small cleanup, docs | No |

Only raise actionable findings with a concrete scenario. Don't block on subjective style
that doesn't violate project standards — Spotless decides formatting.

## Checklist

**Scope** — Does the diff do what the requirement asks, and nothing unrelated (one task per
branch/worktree; other work belongs in its own branch)? Any stray
files (IDE, `target/`, `.env`, debug code, commented-out code)?

**Correctness** — Null/empty/invalid inputs; boundary values; error paths; idempotency
of PUT/DELETE; time zones (`Instant`/`TIMESTAMPTZ`).

**Security** — Parameter binding only; input validated; authorization preserved on every
touched endpoint; no secrets, tokens or personal data in code, logs or error responses;
new dependencies justified and free of known vulnerabilities.

**API** — Paths, methods and status codes per standards; Problem Details error body;
backward compatible (no removed/renamed fields, no tightened validation without a
decision); pagination bounded.

**Database** — New migration (never an edited one); `ddl-auto=validate` still passes;
destructive changes staged; indexes for new FKs/filters; constraints enforce invariants;
fetch strategy avoids N+1; long-running statements on large tables considered.

**Transactions & concurrency** — `@Transactional` on the service boundary;
`readOnly` for reads; no remote calls inside transactions; optimistic locking where
concurrent updates are possible; no shared mutable state in singletons.

**Exceptions & logging** — Specific exceptions mapped in the global handler; nothing
swallowed; log levels sensible; no sensitive data logged.

**Tests** — New behavior covered at the right level (see CLAUDE.md testing table); bug
fixes have a regression test; ITs use Testcontainers PostgreSQL; tests deterministic.

**Architecture** — Layer rules respected; classes in the right layer package (see CLAUDE.md); constructor injection;
DTOs at the API boundary; consistent with surrounding code.

## Self-review questions (before every push)

- Did I modify anything unrelated?
- Does the change fully satisfy the requirement?
- Could this break existing callers, data or deployments?
- Are null/empty/invalid inputs and exceptions handled?
- Are transactions and queries correct and efficient?
- Are authorization rules preserved?
- Are API responses and status codes correct?
- Are the tests sufficient, and do they pass?
- Is any sensitive information exposed?
- Does it follow the existing architecture?

Do not push with known failing tests unless explicitly instructed.

## Output format

```
### Review: <branch or PR #>
Verdict: READY | CHANGES REQUIRED
Gates: verify <pass/fail>

P1 src/main/java/.../TaskService.java:42 — update is not transactional; a failure after
   the first save leaves the task half-updated. Fix: annotate updateTask with @Transactional.
P2 src/test/... — no test for blank title → 400.
```
List "No findings" explicitly when there are none.
