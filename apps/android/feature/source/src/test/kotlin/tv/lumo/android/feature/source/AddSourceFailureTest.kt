package tv.lumo.android.feature.source

import com.google.common.truth.Truth.assertThat
import java.io.IOException
import org.junit.Test
import tv.lumo.android.core.data.LumoError
import tv.lumo.android.network.generated.model.ErrorCode
import tv.lumo.android.network.generated.model.IngestionErrorCode

/**
 * The rule the contract states and this screen has to keep: **no generic message
 * on this surface**.
 *
 * The reason is in the contract itself — the user's next action is completely
 * different between "the server is down" and "your password is wrong", and a
 * screen that collapses them leaves someone editing a correct address for twenty
 * minutes. So every ingestion code gets a sentence of its own, and this test is
 * what stops the next one from quietly falling into "something went wrong".
 */
class AddSourceFailureTest {

    @Test
    fun `every ingestion code has a message of its own`() {
        // By enumeration, not by sample. When the contract gains a code, the
        // generated enum gains a constant, and this fails on the same day rather
        // than in a support conversation.
        IngestionErrorCode.entries.forEach { ingestion ->
            val code = ErrorCode.decode(ingestion.value)
            assertThat(code).isNotNull()

            val failure = LumoError.Api(code!!, detail = null).asFailure()

            assertThat(failure).isNotEqualTo(AddSourceFailure.Unexpected)
        }
    }

    @Test
    fun `the two that look alike are told apart`() {
        // US-06 asks for these two by name: a refusal and an unreachable server
        // must not read the same, because one means "fix your password" and the
        // other means "wait, or check the address".
        assertThat(LumoError.Api(ErrorCode.SOURCE_AUTH_FAILED, null).asFailure())
            .isEqualTo(AddSourceFailure.CredentialsRefused)
        assertThat(LumoError.Api(ErrorCode.SOURCE_UNREACHABLE, null).asFailure())
            .isEqualTo(AddSourceFailure.Unreachable)
    }

    @Test
    fun `an empty playlist is not a success and not a format error`() {
        // US-07 is explicit: SOURCE_EMPTY rather than a success screen over an
        // empty list — and it is a different sentence from "this is not a
        // playlist", because the address was right and the file was read.
        assertThat(LumoError.Api(ErrorCode.SOURCE_EMPTY, null).asFailure())
            .isEqualTo(AddSourceFailure.Empty)
        assertThat(LumoError.Api(ErrorCode.SOURCE_INVALID_FORMAT, null).asFailure())
            .isEqualTo(AddSourceFailure.NotAPlaylist)
    }

    @Test
    fun `the rate limit keeps the server's delay`() {
        val failure = LumoError.Api(ErrorCode.RATE_LIMITED, null, retryAfterSeconds = 30)
            .asFailure()

        assertThat(failure).isEqualTo(AddSourceFailure.TooManyAttempts(30))
    }

    @Test
    fun `a code newer than this build degrades instead of crashing`() {
        // The contract allows new codes within v1 and requires a graceful
        // degradation. This is that branch, and it exists on purpose.
        assertThat(LumoError.UnknownCode("SOURCE_SUSPENDED_BY_PROVIDER").asFailure())
            .isEqualTo(AddSourceFailure.Unexpected)
    }

    @Test
    fun `no network is its own case, because retrying unchanged is worth offering`() {
        assertThat(LumoError.Offline(IOException("airplane mode")).asFailure())
            .isEqualTo(AddSourceFailure.Offline)
    }
}
