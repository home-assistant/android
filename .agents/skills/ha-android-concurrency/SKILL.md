---
name: ha-android-concurrency
description: Home Assistant Android coroutine and threading guidance. Use when launching coroutines, choosing dispatchers, sharing mutable state across threads, exposing Flows, or reviewing for blocking calls and race conditions.
---

# HA Android Concurrency

Use this skill when writing or reviewing anything asynchronous: coroutines, Flows, dispatchers, or shared mutable state.

All concurrency uses Kotlin Coroutines and Flow exclusively.

## Scopes and Lifecycle

- Tie every new coroutine scope to an Android lifecycle (`viewModelScope`, `lifecycleScope`) to prevent leaks.
- Don't create a `CoroutineScope` per object: the lifecycle owner (service, screen) owns the scope and passes it (or the caller's `coroutineScope { }`) to the objects it manages. Creating one scope per session/item multiplies leaks and cancellation bugs.
- A child object must never cancel a scope it doesn't own — cancelling a shared scope kills unrelated siblings. If a class needs its own scope, build it internally and document the required `release()`/`close()` call.
- Never catch `kotlin.coroutines.cancellation.CancellationException` — let it propagate so coroutines cancel properly.

## Never Block a Thread

- Never use `runBlocking`.
- Prefer suspension over blocking everywhere. `synchronized` blocks are forbidden; use `Mutex` or another non-blocking mechanism for concurrent access.
- Race conditions are hard to debug — design concurrent access carefully upfront and make shared references thread-safe or don't share them.

## Dispatchers

- Run heavy work on a dedicated dispatcher: `Default` for CPU-bound work, `IO` for blocking network or storage calls. Don't reflexively add `withContext(Dispatchers.IO)` around calls that are already main-safe (repository suspend functions usually are).
- Only use `Main` for system APIs that require the main thread, and keep the work inside it to the strict minimum: precompute on `Default`, then `withContext(Dispatchers.Main)` for just the UI/system call. Don't run a whole function on Main because one line needs it.
- Make functions main-safe whenever possible; when a function must run on a specific thread, annotate it (`@MainThread`) or document it, and keep the docs accurate when call sites change.
- Confine mutable state (like a session map in a service) to one thread instead of sprinkling synchronization — reads and writes from mixed dispatchers on a plain map is a recurring review finding.

## Mutex and Double-Checked Locking

Use `kotlinx.coroutines.sync.Mutex` (never `synchronized`) to serialize access to shared mutable state. When a value must be initialized exactly once but reads should stay cheap after that, combine a `@Volatile` field with double-checked locking: read the volatile field first without locking, and only take the mutex when it's still unset.

`@Volatile` makes a write by one coroutine/thread immediately visible to others, so the fast path outside the lock never reads a stale or half-published value; the mutex guarantees the initializer runs once even under a concurrent race.

```kotlin
private val mutex = Mutex()

@Volatile private var value: T? = null

suspend fun get(): T {
    value?.let { return it }          // fast path, no lock
    return mutex.withLock {
        value?.let { return it }      // re-check: another coroutine may have set it while we waited
        initializer().also { value = it }
    }
}
```

The project already provides this as `SuspendLazy` in `:common` (`common/src/main/kotlin/io/homeassistant/companion/android/common/util/SuspendLazy.kt`) — reuse it for lazy single initialization instead of re-implementing the pattern. Write the pattern by hand only when the caller isn't `suspend` or the state is reset/invalidated over time. Both re-checks inside the lock are required: dropping the second one reintroduces the race the pattern exists to close.

## Testing Concurrency

- Test concurrent behavior with unit tests under `runTest` using a `TestDispatcher` to control virtual time.
- For the Main-dispatcher swap helpers, `TestDispatcher` choice, and Turbine Flow rules, see the `ha-android-testing` skill.
