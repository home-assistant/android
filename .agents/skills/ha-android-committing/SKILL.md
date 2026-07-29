---
name: ha-android-committing
description: Home Assistant Android change finalization. Use when finishing a change, preparing to commit, updating the changelog, naming a branch, or opening a pull request.
---

# HA Android Committing

Use this skill when finalizing a change and preparing it for commit or a pull request. For evaluating the code itself, use the `ha-android-review` skill.

## Before Committing

1. Format: `./gradlew :build-logic:convention:ktlintFormat ktlintFormat`
2. Tests: `./gradlew test`
3. If the change is visible to end users or changes behavior, add it to `app/src/main/res/xml/changelog_master.xml`.
4. After adding or updating any dependency (in `gradle/libs.versions.toml` or module declarations), run `./gradlew alldependencies --write-locks`.
5. Run the `ha-android-review` skill over the diff before handing it off.

Branch naming: `feature/add-dark-mode`, `fix/crash-on-rotation`.

## Pull Requests

- Use `.github/pull_request_template.md` as the PR body.
- Keep PRs small — easier to review, faster to merge. Break large changes into logical chunks; a reusable component (a new `HA*` composable, a shared utility) deserves its own PR before the feature that uses it.
- A PR must not contain changes unrelated to its purpose. Found a bug on the way? Open an issue and a separate PR. Revert incidental edits to files the change doesn't need.
- Pure refactor or code-move PRs must not change behavior — keep copied code as it was and fix pre-existing issues in follow-ups.
- Once a PR is open, merge `main` into the branch instead of rebasing.
- Keep the PR description and screenshots up to date as the implementation evolves.
- Tests belong in the same PR as the implementation they cover, including tests moved along with moved logic.
- Features visible in `:app` must be verified on (or hidden from) Automotive and other form factors like Meta Quest.
- When the Android Gradle Plugin is updated, refresh the lint baseline with `./gradlew updateLintBaseline`. Never grow the baseline to silence an issue introduced by your change — fix the source or delete the unused resource instead.
