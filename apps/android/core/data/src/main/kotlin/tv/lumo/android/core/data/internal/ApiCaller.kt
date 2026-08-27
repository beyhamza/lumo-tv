package tv.lumo.android.core.data.internal

import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import retrofit2.Response
import tv.lumo.android.core.data.LumoError
import tv.lumo.android.core.data.LumoResult

/**
 * The single crossing point between Retrofit and the rest of the applications.
 *
 * Every repository call goes through here, which is what makes "the same failure
 * looks the same on every screen" true by construction rather than by review.
 *
 * <h2>What each failure becomes</h2>
 *
 * | What happened | Result |
 * |---|---|
 * | No network, DNS failure, timeout | [LumoError.Offline] — the only case where a plain retry helps |
 * | A problem document with a known code | [LumoError.Api] |
 * | A problem document with a newer code | [LumoError.UnknownCode] |
 * | A body that could not be parsed | [LumoError.Unreadable] |
 *
 * <h2>A 401 is not special here, on purpose</h2>
 *
 * `TokenAuthenticator` has already tried a refresh and replayed the request by
 * the time anything reaches this class. A 401 that survives that means the
 * session is genuinely gone, and `UNAUTHENTICATED` is the honest thing to hand a
 * screen — retrying the refresh from here would be the second refresh in flight
 * that US-04 spends a page warning about.
 *
 * <h2>Cancellation is not a failure</h2>
 *
 * A screen that closes cancels its coroutines, and that arrives as a
 * [CancellationException]. Catching it into a `Failure` would leave the caller
 * looking at "something went wrong" on a screen the user just left, and would
 * break structured concurrency besides — so it is rethrown before anything else
 * is caught.
 *
 * <h2>Stateless, and it has to be</h2>
 *
 * A singleton shared by every repository, so several calls are in flight through
 * this object at once as a matter of course — three screens starting at once is
 * the ordinary case, not the edge one. Nothing about one call is stored on the
 * instance; [attempt] hands the outcome back instead.
 */
@Singleton
internal class ApiCaller @Inject constructor(
    private val problems: ProblemReader,
) {

    /** For an operation whose success carries a body. */
    suspend fun <T : Any> call(block: suspend () -> Response<T>): LumoResult<T> =
        when (val attempt = attempt(block)) {
            is LumoResult.Failure -> attempt
            is LumoResult.Success -> {
                val response = attempt.value
                when {
                    !response.isSuccessful -> failure(response)
                    else -> response.body()
                        ?.let { LumoResult.Success(it) }
                        ?: LumoResult.Failure(LumoError.Unreadable(response.code(), null))
                }
            }
        }

    /** For an operation that answers `204 No Content`. */
    suspend fun empty(block: suspend () -> Response<Unit>): LumoResult<Unit> =
        when (val attempt = attempt(block)) {
            is LumoResult.Failure -> attempt
            is LumoResult.Success ->
                if (attempt.value.isSuccessful) {
                    LumoResult.Success(Unit)
                } else {
                    failure(attempt.value)
                }
        }

    /**
     * Runs the call and reports whether it produced an HTTP answer at all.
     *
     * The answer itself is the success value — [LumoResult] is already the right
     * shape for "one of two things happened", and reusing it here keeps a second
     * private result type out of the module. Note that a [LumoResult.Success]
     * holding a `500` is still a success *at this level*: it means the server
     * answered, and reading that answer is the caller's next step.
     */
    private suspend fun <T> attempt(
        block: suspend () -> Response<T>,
    ): LumoResult<Response<T>> =
        try {
            LumoResult.Success(block())
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (io: IOException) {
            LumoResult.Failure(LumoError.Offline(io))
        } catch (other: Exception) {
            // A success body Moshi could not read — most often an enum value
            // added to the contract after this build shipped. There is no status
            // to report: the converter runs inside the call.
            LumoResult.Failure(LumoError.Unreadable(status = null, cause = other))
        }

    private fun failure(response: Response<*>): LumoResult.Failure {
        val body = try {
            response.errorBody()?.string()
        } catch (io: IOException) {
            null
        }
        return LumoResult.Failure(problems.read(response.code(), body))
    }

}
