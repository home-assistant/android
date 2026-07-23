---
name: ha-android-kotlin-style
description: Home Assistant Android Kotlin style. Use when declaring constants, modeling data or logic types, working with time or dates, choosing visibility, naming, or writing KDoc and comments.
---

# HA Android Kotlin Style

Use this skill when writing or reviewing Kotlin code style: constants, types, immutability, time, visibility, and documentation.

KTLint enforces formatting via `.editorconfig`; format with `./gradlew :build-logic:convention:ktlintFormat ktlintFormat`. Style rationale lives at https://developers.home-assistant.io/docs/android/codestyle and https://developers.home-assistant.io/docs/android/best_practices.

## Constants

Replace magic numbers and strings with named constants, placed near their usage:

```kotlin
// Good - private, at file level, outside any companion object
private const val MAX_RETRIES = 3

// For constants shared across a module, a dedicated *Constants.kt file with object namespacing
object NetworkConstants {
    const val MAX_RETRIES = 3
}
```

Avoid `companion object` for constants unless they must be accessed externally under the class name or several classes in one file need the same constant name. Use `@VisibleForTesting` when a constant is only exposed for tests.

## Strong Types, Never Strings for Logic

- Use sealed classes/interfaces over enums or strings for logic control. Sealed subtypes can carry their own data, and `when` without an `else` branch makes the compiler catch missing cases.
- Reserve strings for raw third-party values or UI display; wrap unavoidable strings in a strong type such as an inline value class. Avoid string manipulation to build structured values (URLs, identifiers) — work with the typed object (`Url`, a value class) and convert at the boundary.
- Never use `Pair`/`Triple` in an API — a caller can't tell what the components mean. Define a small data class with named fields instead.
- Use Kotlin `Duration`/`Instant` instead of primitive types for time. If a primitive is unavoidable, put the unit in the name (`THRESHOLD_MS`), and convert to a strong type as early as possible. Note that `Duration` is not a compile-time constant: `val STOP_TIMEOUT = 500.milliseconds`, not `const val`.
- Locale-sensitive operations like `lowercase()` must not feed logic: for sort keys, identifiers, and search matching use `lowercase(Locale.ROOT)`, otherwise matching breaks under locales like Turkish (dotted/dotless I).
- Never use `System.currentTimeMillis`; use `kotlin.time.Clock` injected through Hilt, and expose the clock as a parameter or attribute so tests can control it.

## Immutability and Functions

- Store data in immutable classes exposing `copy()` or functions returning a new modified instance.
- Keep functions small (under 50 lines), single-responsibility, with meaningful names. If a function is hard to name, it does too much.
- Prefer composition over inheritance.

## Keep It Direct

- Don't introduce an abstraction (interface, seam, wrapper) for a single implementation or a hypothetical future need — use the proven library or API directly. Abstract only when a second real use appears, or to hide an implementation from outside its module (public interface, `internal` implementation bound through DI) — see `ha-android-architecture` for the interface + `*Impl` criteria.
- Don't store what can be derived: a field duplicating a map key, an ID rebuildable from its parts, or a flag mirroring other state is a bug waiting for a desync.
- When calling a function with several parameters of the same or primitive types, use named parameters instead of relying on order.

## Visibility

Default to the narrowest visibility: private to the file or class. Before making anything public, check whether `internal` suffices.

## Imports

When adding an import, add the import and its usage in the same edit — linters and hooks strip unused imports, so an import added in a separate step gets removed. The reverse also bites: after removing or moving code, delete the imports that became unused — ktlint fails CI on them.

## Documentation and Comments

- Every public function needs KDoc focused on the functionality, not the implementation. Mention implementation details only when they affect usage (synchronization constraints, what it throws).
- Keep it to the essential and straight to the point: state the contract in as few words as it takes and stop. Don't narrate the implementation, restate the obvious from the signature, explain rationale that belongs in a commit or issue, or pad with AI-sounding prose — a one-line function usually needs a one-line doc.
- Keep KDoc in sync with the code: when changing what a function does, targets, or emits, update its documentation in the same change — stale KDoc naming the wrong class or error type misleads the next reader.
- Explain the "why", not just the "what". Write for developers who may not be native English speakers: no abbreviations, add references when useful.
- Avoid TODOs; if one is required, link an existing GitHub issue: `// TODO Missing feature https://github.com/home-assistant/android/issues/404`. Don't invent an issue number or open a new issue yourself — ask the user for the issue, or leave the TODO out.
- GitHub references in comments and KDoc are always full HTTP links, never bare `#1123` — a number is ambiguous and unclickable outside GitHub. When the reference points at code (a file, line, or commit), use a permalink (commit-SHA URL, `y` on GitHub) so the link survives file moves and edits.
