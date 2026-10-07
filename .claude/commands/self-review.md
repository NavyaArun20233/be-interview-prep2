---
description: Review the current branch's changes against project standards before pushing
---

Perform a senior-backend-engineer review of the current branch, following
`docs/code-review.md` exactly and the standards in `CLAUDE.md`.

1. Confirm you are inside the task's worktree, not the main checkout
   (`git worktree list`). Run `git status`, `git branch --show-current`, `git fetch origin`, then
   `git diff origin/main...HEAD`, `git diff` and `git diff --cached`.
2. Read every changed file in full context.
3. Run `./mvnw -B -ntp verify` (`.\mvnw.cmd` on Windows) if `pom.xml` exists; otherwise
   state that no build exists yet.
4. Report findings using the output format in `docs/code-review.md`
   (P0–P3, `file:line`, concrete failure scenario, suggested fix).

Do not commit, push or modify files during this command — report only.

$ARGUMENTS
