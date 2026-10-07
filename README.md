# be-interview-prep2

Java 21 / Spring Boot / PostgreSQL backend, built with Maven.

> **Status:** the development harness and the Spring Boot project skeleton (build, Maven
> wrapper, Problem Details error handling, health check, Testcontainers tests) are in
> place. No features have been implemented yet.

## Prerequisites

- JDK 21
- Docker (integration tests use Testcontainers PostgreSQL)
- No global Maven needed. Use the wrapper (`./mvnw`, or `.\mvnw.cmd` on Windows)
- [GitHub CLI](https://cli.github.com) (`gh`) for PRs

## Common commands

| Purpose                       | Command                     |
|-------------------------------|-----------------------------|
| Format                        | `./mvnw spotless:apply`      |
| Unit tests                    | `./mvnw test`                |
| Full verification (as CI)     | `./mvnw -B -ntp verify`      |
| Run locally                   | `./mvnw spring-boot:run`     |

Running locally needs a PostgreSQL instance, for example:

```sh
docker run --rm -d --name prep-postgres -p 5432:5432 \
  -e POSTGRES_DB=prep -e POSTGRES_USER=prep -e POSTGRES_PASSWORD=prep postgres:16
```

Connection settings are supplied via environment variables
(`SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`).
Never commit credentials.

## Development workflow (one task = one branch = one worktree)

Each task gets its own branch, checked out in its own
[git worktree](https://git-scm.com/docs/git-worktree) next to the main checkout. The main
checkout stays on `main`:

```
Desktop/
  be-interview-prep2/                       # main checkout (main, kept clean)
  be-interview-prep2.worktrees/
    feature-q1-task-api/                    # branch feature/q1-task-api
    bugfix-task-404/                        # branch bugfix/task-404
```

1. From the main checkout: `git switch main && git pull --ff-only`, then
   `git worktree add -b feature/<task> ../be-interview-prep2.worktrees/feature-<task> main`
   (Claude Code: `/start-task feature/<task>`).
2. Work only inside that worktree. Implement with tests, following [CLAUDE.md](CLAUDE.md).
3. `./mvnw spotless:apply && ./mvnw -B -ntp verify`.
4. Self-review the diff with [docs/code-review.md](docs/code-review.md)
   (Claude Code: `/self-review`).
5. Conventional commit, push, open a PR (template is pre-filled).
6. CI (`.github/workflows/ci.yml`) must pass. Address review comments, and merge only when
   all required checks and approvals are in place.
7. After merge: `git worktree remove ../be-interview-prep2.worktrees/<dir>`,
   `git branch -d <branch>`, `git pull` on `main`.

## Repository settings (maintainer, one-time)

Enable branch protection on `main` in GitHub → Settings → Branches: require a pull
request, require the **Build and test** status check, require at least one approval,
and disallow force pushes. These settings cannot be committed to the repository.
