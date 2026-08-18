# Source quality complex method detail

## Goal

Allow a developer to select a high-complexity method and inspect its indexed source body and quality metrics.

## Constraints

- Keep method bodies out of the dashboard response.
- Fetch only the explicitly selected project-scoped method.
- Use the global Toast for request failures.

## Non-goals

- Editing or downloading the source.
- Recalculating complexity in the frontend.

## Steps

1. Add a project-scoped method detail REST contract.
2. Add graph reader, service, and controller tests.
3. Add a click-driven source detail modal to the quality screen.
4. Run the deterministic validation gate.

## Validation

- `gradlew.bat test --tests "com.hanwha.ai.sourcequality.*" --console=plain`: passed
- `gradlew.bat verifyAll --console=plain`: passed
- `git diff --check`: passed

## Risks and rollback

The response contains uploaded source and must stay project-scoped. Rollback removes the detail endpoint and modal without changing persisted data.
