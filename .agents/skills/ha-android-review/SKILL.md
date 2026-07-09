---
name: ha-android-review
description: Home Assistant Android PR and review guidance. Use when preparing a commit or pull request, reviewing changes, updating the changelog, naming branches, or checking CI and security requirements.
---

# HA Android Review

Use this skill when reviewing Home Assistant Android changes or preparing a commit or pull request.

## Before Committing

1. Format: `./gradlew :build-logic:convention:ktlintFormat ktlintFormat`
2. Tests: `./gradlew test`
3. If the change is visible to end users or changes behavior, add it to `app/src/main/res/xml/changelog_master.xml`.
4. After adding or updating any dependency (in `gradle/libs.versions.toml` or module declarations), run `./gradlew alldependencies --write-locks`.

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

## Review Checklist

Load the skill covering each dimension the change touches and review against its rules — the rules live in the skills, not here, so this list stays a router:

- Kotlin style: constants, strong types, immutability, visibility, KDoc — `ha-android-kotlin-style`.
- Coroutines and threading: scopes, dispatchers, shared state, blocking calls — `ha-android-concurrency`.
- Logging and errors: catch blocks, `CancellationException`, sensitive data, FailFast — `ha-android-logging-errors`.
- UI: Compose, HATheme, ViewState, navigation, widgets — `ha-android-ui`.
- Structure: modules, layers, ViewModels, repositories, server-version gating, DI, storage — `ha-android-architecture`.
- Tests: unit, Turbine, Robolectric, screenshot and interaction tests — `ha-android-testing`.

One review point that has no other home: reuse before rewrite — check whether the logic already exists (pickers, url handling, shared utils) and extract shared code instead of duplicating it.

## Engineering Values

- **Mechanism over reminder**: when the same review comment keeps coming back, encode it instead of repeating it — a custom lint rule in `:lint`, a KTLint override in `.editorconfig`, a `FailFast` check, a module-wide test listener, or an update to these skills. A convention that relies on people remembering it will keep being violated.
- **Root cause before fix**: a bug fix must state the actual cause and how to reproduce it. Don't patch symptoms; if the cause is external (platform, WebView, library), document the evidence and the repro steps rather than adding speculative workarounds.

## Review Tone

Be kind and respectful. Give hints instead of orders, and use examples to explain issues.

## Security

- GitHub Actions: use the most restrictive permissions — don't request write when read (or none) suffices.
- Never commit tokens or secrets; use GitHub Secrets for CI/CD.
- Use well-known, maintained libraries or stick to the Android SDK.
