package tv.lumo.android.feature.auth

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import tv.lumo.android.core.data.LumoError
import tv.lumo.android.network.generated.model.ErrorCode

/**
 * Which refusal gets which sentence (US-03).
 *
 * The only pure part of the Google flow — the rest needs an account picker and a
 * device — and the part that goes wrong when the contract gains a code. Three of
 * these four say something specific because they lead somewhere different: one is
 * a wait, one is a plan limit with two ways out, and one is not the user's doing
 * at all.
 */
class GoogleSignInFailureTest {

    @Test
    fun `a token the server refused does not blame the account`() {
        // OAUTH_TOKEN_INVALID means the token failed verification, which in
        // practice is a build carrying the wrong OAuth client ID far more often
        // than it is anything the user can act on.
        val failure = LumoError.Api(ErrorCode.OAUTH_TOKEN_INVALID, detail = null).asGoogleFailure()

        assertThat(failure).isEqualTo(GoogleSignInFailure.Rejected)
    }

    @Test
    fun `the rate limit carries the server's own delay`() {
        val failure = LumoError
            .Api(ErrorCode.RATE_LIMITED, detail = null, retryAfterSeconds = 45)
            .asGoogleFailure()

        // A wait, not a fault. Without the number the screen can only say
        // "something went wrong", which invites an immediate second attempt.
        assertThat(failure).isEqualTo(GoogleSignInFailure.TooManyAttempts(45))
    }

    @Test
    fun `a rate limit without a readable delay says so rather than inventing one`() {
        val failure = LumoError.Api(ErrorCode.RATE_LIMITED, detail = null).asGoogleFailure()

        assertThat(failure).isEqualTo(GoogleSignInFailure.TooManyAttempts(null))
    }

    @Test
    fun `the device ceiling is the same refusal as an email sign-in`() {
        // The plan's limit does not care how the session was going to be opened,
        // and the user is told the same two ways out.
        val failure = LumoError.Api(ErrorCode.DEVICE_LIMIT_REACHED, detail = null).asGoogleFailure()

        assertThat(failure).isEqualTo(GoogleSignInFailure.DeviceLimitReached)
    }

    @Test
    fun `a code this build has never heard of degrades instead of crashing`() {
        // The contract says new codes may appear within v1. `UnknownCode` is what
        // core:data produces for one, and it must reach a sentence rather than an
        // exception — a client that crashed on a new code would make adding one a
        // breaking change.
        assertThat(LumoError.UnknownCode("SOMETHING_NEW").asGoogleFailure())
            .isEqualTo(GoogleSignInFailure.Unexpected)
    }

    @Test
    fun `a code that belongs to another flow is not given a Google sentence`() {
        // INVALID_CREDENTIALS cannot happen here — there is no password — and if
        // it somehow did, saying "that email and password do not go together" on
        // a screen with neither would be worse than saying nothing specific.
        assertThat(LumoError.Api(ErrorCode.INVALID_CREDENTIALS, detail = null).asGoogleFailure())
            .isEqualTo(GoogleSignInFailure.Unexpected)
    }

    @Test
    fun `a request that never left the device says so`() {
        assertThat(LumoError.Offline(java.io.IOException("no route")).asGoogleFailure()).isEqualTo(GoogleSignInFailure.Offline)
    }
}
