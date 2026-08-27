package tv.lumo.android.core.data.internal

import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import javax.inject.Inject
import javax.inject.Singleton
import tv.lumo.android.core.data.FieldProblem
import tv.lumo.android.core.data.LumoError
import tv.lumo.android.network.generated.model.ErrorCode

/**
 * Reads an RFC 7807 error body into a [LumoError]. Once, for the whole product.
 *
 * <h2>Why the body is read generically instead of as the generated `Problem`</h2>
 *
 * This is the one place where not using the generated model is the right answer,
 * and the reason is the generated model itself: `Problem.code` is typed as the
 * `ErrorCode` **enum**, non-nullable. Moshi throws on an enum value it does not
 * know, so handing a `Problem` adapter a code added to the contract after this
 * build shipped does not produce "an unrecognised code" — it produces a parse
 * failure, and the reason the server refused is lost entirely.
 *
 * The contract requires the opposite behaviour in as many words: new codes may
 * appear within v1, and a client must degrade gracefully rather than crash. A
 * strict adapter cannot do that, so the body is read as plain JSON and the code
 * is decoded afterwards, by the generated enum's own [ErrorCode.decode] — which
 * returns null for anything it does not know, which is exactly the branch
 * [LumoError.UnknownCode] exists for.
 *
 * Note what this is *not*: no request or response shape is declared by hand here
 * (ADR 0001). Three field names are read out of a generic JSON map, and the
 * mapping from string to code is the generated enum's own.
 */
@Singleton
internal class ProblemReader @Inject constructor(moshi: Moshi) {

    private val jsonObject = moshi.adapter<Map<String, Any?>>(
        Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java),
    )

    /**
     * @param status the HTTP status, kept only for [LumoError.Unreadable] — never
     * branched on. `409` alone covers a source still importing, an expired
     * subscription and a stream limit reached, which are three different things
     * to tell a user.
     * @param body the error body, or null when there was none to read.
     */
    fun read(status: Int, body: String?): LumoError {
        if (body.isNullOrBlank()) return LumoError.Unreadable(status, null)

        val root = try {
            jsonObject.fromJson(body)
        } catch (failure: Exception) {
            // A proxy's HTML error page, a truncated body, anything that is not
            // the problem document the contract promises.
            return LumoError.Unreadable(status, failure)
        } ?: return LumoError.Unreadable(status, null)

        val raw = root["code"] as? String ?: return LumoError.Unreadable(status, null)
        val code = ErrorCode.decode(raw) ?: return LumoError.UnknownCode(raw)

        return LumoError.Api(
            code = code,
            detail = root["detail"] as? String,
            fields = fieldProblems(root["errors"]),
        )
    }

    /**
     * `Problem.errors[]`, present on validation failures only.
     *
     * An entry that is not shaped like one is dropped rather than guessed at: a
     * field message rendered next to the wrong input is worse than no field
     * message, because the form-level message still reaches the user either way.
     */
    private fun fieldProblems(value: Any?): List<FieldProblem> {
        val entries = value as? List<*> ?: return emptyList()

        return entries.mapNotNull { entry ->
            val problem = entry as? Map<*, *> ?: return@mapNotNull null
            val field = problem["field"] as? String ?: return@mapNotNull null
            val code = problem["code"] as? String ?: return@mapNotNull null
            FieldProblem(field = field, code = code, detail = problem["detail"] as? String)
        }
    }
}
