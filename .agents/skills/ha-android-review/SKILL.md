---
name: ha-android-review
description: Home Assistant Android code review guidance. Use when reviewing changes or a diff for correctness, style, and convention adherence, or checking security requirements.
---

# HA Android Review

Use this skill when reviewing Home Assistant Android changes. For finalizing your own change — format, tests, changelog, branch, and PR — use the `ha-android-committing` skill.

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
