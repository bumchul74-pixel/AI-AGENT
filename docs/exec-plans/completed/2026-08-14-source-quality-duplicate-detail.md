# Source quality duplicate detail

## Goal

Allow a developer to select a duplicate-method group and compare the method bodies that caused the duplicate classification.

## Constraints

- Keep method bodies out of the dashboard response.
- Fetch source only for the explicitly selected project-scoped group.
- Do not log method bodies or hashes with source content.

## Non-goals

- Editing source files from the quality screen.
- Implementing a semantic diff engine.

## Steps

1. Add a project-scoped duplicate-group detail query and REST contract.
2. Add service and controller tests.
3. Add a click-driven comparison modal to the quality screen.
4. Run deterministic backend and frontend validation.

## Validation

- `gradlew.bat test`
- `gradlew.bat frontendCheck`
- `git diff --check`

## Risks and rollback

The detail response contains uploaded source. It is returned only after an explicit group selection and remains project-scoped. Rollback removes the detail endpoint and modal without changing stored data.
