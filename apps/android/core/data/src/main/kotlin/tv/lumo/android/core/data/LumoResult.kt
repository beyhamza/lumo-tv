package tv.lumo.android.core.data

/**
 * The outcome of one call: a value, or a [LumoError].
 *
 * Not Kotlin's own `Result`. That one carries a `Throwable`, which would mean
 * every caller unwrapping an exception to find out what happened — and an
 * exception is precisely the shape this module exists to stop propagating. It
 * also swallows the distinction the screens care most about, between "the server
 * said no, here is why" and "the request never left the device".
 */
sealed interface LumoResult<out T> {

    data class Success<out T>(val value: T) : LumoResult<T>

    data class Failure(val error: LumoError) : LumoResult<Nothing>
}

/** The value, or null when the call failed. For a caller that has a fallback. */
fun <T> LumoResult<T>.valueOrNull(): T? = when (this) {
    is LumoResult.Success -> value
    is LumoResult.Failure -> null
}

/** The error, or null when the call succeeded. */
fun <T> LumoResult<T>.errorOrNull(): LumoError? = when (this) {
    is LumoResult.Success -> null
    is LumoResult.Failure -> error
}

/**
 * Maps the value, leaving a failure untouched.
 *
 * This is what keeps mapping code out of the repositories' error paths: a
 * repository writes the happy path once and the failure travels through
 * unchanged.
 */
inline fun <T, R> LumoResult<T>.map(transform: (T) -> R): LumoResult<R> = when (this) {
    is LumoResult.Success -> LumoResult.Success(transform(value))
    is LumoResult.Failure -> this
}
