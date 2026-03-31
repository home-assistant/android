package io.homeassistant.companion.android.common.util.di

/**
 * A class similar to [javax.inject.Provider] but where [invoke] is a suspend function. This is useful
 * for things that require a coroutine context.
 *
 * Example usage:
 * ```
 * class MyRepository(private val slowDiskService: SuspendProvider<DiskService>) {
 *     suspend fun fetchData(): Data {
 *         return slowDiskService().getData()
 *     }
 * }
 * ```
 * In this example, `slowDiskService` is a `SuspendProvider` that provides an instance of `ApiService`.
 * The `invoke()` operator is called to get the `DiskService` instance, which involve
 * asynchronous initialization, to read from the disk.
 *
 * @param T The type of the instance provided.
 */
fun interface SuspendProvider<T> {
    suspend operator fun invoke(): T
}
