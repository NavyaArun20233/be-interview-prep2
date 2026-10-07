---
description: Create a branch and its own git worktree for a new task (one task = one branch = one worktree)
argument-hint: <type>/<short-kebab-name>, e.g. feature/q1-task-api
---

Set up an isolated branch + worktree for the task `$ARGUMENTS`, following the
"Worktrees" section of `CLAUDE.md`.

1. Validate the argument: it must be `<type>/<name>` with `<type>` one of `feature`,
   `bugfix`, `refactor`, `chore`, `docs`, and `<name>` short kebab-case. If it is missing
   or invalid, stop and ask for one.
2. Locate the main checkout: the first entry of `git worktree list` (call it `MAIN`).
   The worktree directory is `MAIN/../be-interview-prep2.worktrees/<branch with "/"
   replaced by "-">`.
3. Preflight in `MAIN`: `git -C MAIN status --porcelain` must be empty and
   `git -C MAIN branch --show-current` must be `main`. Otherwise stop and report. Never
   stash, reset or switch away from someone's work.
4. Stop and report if the branch already exists locally or on `origin`
   (`git branch --list <branch>`, `git ls-remote --heads origin <branch>`) or the
   directory already exists. Never reuse another task's branch.
5. Update and create: `git -C MAIN fetch origin`, `git -C MAIN pull --ff-only origin main`
   (skip the pull with a note if `origin/main` doesn't exist yet), then
   `git -C MAIN worktree add -b <branch> <worktree-dir> main`.
6. Report: branch name, absolute worktree path, base commit (`git -C <worktree-dir> log
   --oneline -1`), and `git worktree list`. State that all further work for this task
   happens inside that path.

Do not edit, commit or push anything else.
