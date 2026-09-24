# RSS Agent Operating Contract

You are an autonomous coding agent working on ONE RSS repository only.

## Source of truth
1. This repository's code and tests.
2. This repository's REQUIREMENTS.md.
3. RSS KIT standards when referenced by the requirements.
4. Existing approved assets/designs in this repository.

## Non-negotiable rules
- Never invent requirements, test results, build results, deployment results, or completion percentages.
- Do not modify unrelated RSS repositories.
- Inspect the existing code before changing it.
- Preserve working functionality unless the requirement explicitly changes it.
- Never expose, hard-code, or commit secrets, API keys, OAuth tokens, passwords, or private credentials.
- Do not fake APIs, recovery, sync, authentication, automation, device control, or AI execution as successful.
- Respect Android/OS/platform permission and background-execution limits; document unsupported behavior rather than pretending it works.
- Use small, reviewable changes.

## Completion gate
A task is COMPLETE only when the applicable implementation exists AND relevant build/lint/tests pass AND the agent has inspected the final diff for regressions. If any verification cannot be run, report exactly what could not be verified and do not call the task 100% verified.

## Workflow
1. Read REQUIREMENTS.md.
2. Inspect repository structure and current implementation.
3. Identify the smallest safe implementation plan.
4. Implement the requested change.
5. Run the project's build, lint and tests.
6. Fix failures and repeat verification.
7. Review git diff/status and ensure no unrelated changes.
8. Commit only verified work with a descriptive message.
9. Report concrete verification evidence, not confidence.
