package tv.lumo.android.core.data

import tv.lumo.android.network.generated.model.ErrorCode

/**
 * Everything that can go wrong between a screen and the server, said once.
 *
 * <h2>Why this type exists at all</h2>
 *
 * Without it, each of the eight feature modules writes its own `catch`, its own
 * reading of the error body and its own idea of what "offline" means — and they
 * disagree, because there is no reason for eight independent guesses to agree.
 * A screen never sees a [retrofit2.Response], an [okio.IOException] or a raw
 * JSON body; it sees one of the four cases below.
 *
 * <h2>Branching on the code, and on nothing else</h2>
 *
 * The contract is explicit: `Problem.code` is the only field a client may branch
 * on. Not the HTTP status, which is deliberately coarse — `409` covers a source
 * still importing, an expired subscription and a stream limit reached, and those
 * are three different sentences to show. Not `title` or `detail` either: they
 * are English, non-localised, and meant for logs.
 *
 * That is why [Api] carries the generated [ErrorCode] rather than a copy of it.
 * The enum comes from `openapi.yaml`; keeping a hand-written twin here would be
 * a second source of truth for the one thing the contract says is authoritative.
 *
 * <h2>[UnknownCode] is not defensive padding</h2>
 *
 * The contract states that new codes may be added within v1 and that a client
 * must degrade gracefully rather than crash. So an unrecognised code is a
 * *normal* case with its own branch, not an assertion failure — an older
 * application meeting a newer server is the expected shape of this product, not
 * an accident.
 *
 * The practical rule that follows, and it applies to every `when` written over
 * these types anywhere in the applications: **always write the `else`.** A `when`
 * over a generated enum with no `else` compiles today and stops compiling on the
 * next regeneration, which is the good outcome — the bad one is the same `when`
 * with an `else` that nobody thought about, silently filing a new code under
 * "unknown error".
 */
sealed interface LumoError {

    /**
     * The request never reached the server, or its answer never came back.
     *
     * A train, a lift, a TV box whose Wi-Fi dropped. Distinct from every other
     * case because it is the only one where retrying the same call unchanged is
     * a sensible thing to offer — and, for a read, the only one where the cache
     * is the right answer instead of a message.
     */
    data class Offline(val cause: Throwable) : LumoError

    /** The server refused, and named a reason this build knows. */
    data class Api(
        val code: ErrorCode,
        /** English, for logs and bug reports. Never rendered to a user. */
        val detail: String?,
        /** Populated on `VALIDATION_FAILED`, empty otherwise. */
        val fields: List<FieldProblem> = emptyList(),
    ) : LumoError

    /**
     * The server named a reason this build does not know.
     *
     * A newer API than the installed application. The raw code is kept so a bug
     * report says which one, rather than "unknown error" and a shrug.
     */
    data class UnknownCode(val code: String) : LumoError

    /**
     * An answer that could not be read.
     *
     * A proxy's HTML error page, a truncated body, or a success payload carrying
     * an enum value this build's generated models do not have — the last of which
     * is the same "older client, newer server" story as [UnknownCode], seen from
     * the parser rather than from the code.
     *
     * Kept apart from [Api] on purpose: there is no code to branch on, so the only
     * honest thing a screen can say is that something went wrong.
     *
     * @param status null when the body failed to convert inside the call itself,
     * which is where a success payload is parsed — there is no response object to
     * read a status off at that point.
     */
    data class Unreadable(val status: Int?, val cause: Throwable?) : LumoError
}

/**
 * One field the server refused, from `Problem.errors[]`.
 *
 * `field` is a JSON pointer into the request body (`/m3u_url`), which is also
 * the name of the input that produced it — the forms are built from the
 * contract's property names precisely so that this mapping is a `removePrefix`
 * and not a lookup table nobody maintains.
 *
 * `code` is a plain string in the contract, not an enum, so there is nothing to
 * degrade here: it is passed through as it arrived.
 */
data class FieldProblem(
    val field: String,
    val code: String,
    val detail: String?,
) {
    /** `/m3u_url` → `m3u_url`. */
    val name: String get() = this.field.removePrefix("/")
}
