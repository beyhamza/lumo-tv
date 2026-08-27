package tv.lumo.android.feature.auth

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The one rule US-01 states twice: the button stays disabled and the unmet rule
 * is shown **before** submission, not after.
 *
 * Pure state, so it is testable without a device — and worth testing, because it
 * is the difference between this screen and an ordinary form, and the kind of
 * thing a refactor quietly loosens.
 */
class SignUpStateTest {

    @Test
    fun `an untouched form shows no error and cannot be sent`() {
        val state = SignUpState()

        // Null, not false: a form that greets someone in red reads as broken.
        assertThat(state.passwordTooShort).isNull()
        assertThat(state.canSubmit).isFalse()
    }

    @Test
    fun `a short password is called out before anything is submitted`() {
        val state = SignUpState(email = "someone@example.test", password = "short")

        assertThat(state.passwordTooShort).isTrue()
        assertThat(state.canSubmit).isFalse()
    }

    @Test
    fun `exactly the minimum is enough`() {
        // Ten, not eleven. The server's rule is `length < 10` refused, so an
        // off-by-one here would refuse a password the server accepts — the worst
        // of the three possible disagreements, because only the client is wrong.
        val state = SignUpState(email = "someone@example.test", password = "0123456789")

        assertThat(state.passwordTooShort).isFalse()
        assertThat(state.canSubmit).isTrue()
    }

    @Test
    fun `an address is required, and whitespace is not an address`() {
        val state = SignUpState(email = "   ", password = "a long enough password")

        assertThat(state.canSubmit).isFalse()
    }

    @Test
    fun `nothing can be sent twice while the first is in flight`() {
        val state = SignUpState(
            email = "someone@example.test",
            password = "a long enough password",
            submitting = true,
        )

        assertThat(state.canSubmit).isFalse()
    }
}
