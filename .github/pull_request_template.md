## Summary
<!-- What changed, in one or two sentences. Link the task/issue. -->

## Problem
<!-- What problem does this solve, and why is the change needed? -->

## Approach
<!-- How was it solved? Key components, flow and affected modules. -->

## Decisions & Trade-offs
<!-- Each significant decision, the alternatives considered, and what was given up.
     e.g. "Chose X over Y because ...; trade-off: ..." -->

## How to Test
<!-- Exact commands and manual steps a reviewer can run, with expected results.
     e.g. `./mvnw -B -ntp verify`; curl examples and the expected status/body. -->

## Database Changes
<!-- New Flyway migrations, data changes, rollout/backfill steps — or "None". -->

## API Changes
<!-- Endpoints added/modified, request/response changes, compatibility — or "None". -->

## Risks
<!-- What could break? Deployment considerations, config/env vars, rollback plan. -->

## Checklist
- [ ] Build passes
- [ ] Tests pass
- [ ] Static analysis passes
- [ ] Formatting passes (`./mvnw spotless:check`)
- [ ] No secrets committed
- [ ] API compatibility checked
- [ ] Database impact checked
- [ ] Tests added/updated
- [ ] Git diff reviewed (`docs/code-review.md`)
