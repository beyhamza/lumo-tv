package tv.lumo.android.core.common.log

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Redaction is security logic, so it ships with its tests (AGENTS.md §5).
 *
 * The cases that matter are the ones where a URL carries credentials in a shape
 * a naive "strip the query string" helper would miss — which is exactly what
 * Xtream panels do: they put the username and the password in the *path*.
 */
class RedactTest {

    @Test
    fun `an xtream stream url keeps no credential and no host`() {
        // The canonical Xtream shape: /live/<username>/<password>/<id>.ts
        val redacted = Redact.url("http://panel.example.com:8080/live/someuser/somepass/1234.ts")

        assertThat(redacted).doesNotContain("someuser")
        assertThat(redacted).doesNotContain("somepass")
        assertThat(redacted).doesNotContain("panel.example.com")
        assertThat(redacted).doesNotContain("1234")
        // Enough shape left to tell one failure from another.
        assertThat(redacted).isEqualTo("http://<host>/…(4 segments)")
    }

    @Test
    fun `credentials in a query string do not survive either`() {
        val redacted =
            Redact.url("https://panel.example.com/player_api.php?username=u&password=p")

        assertThat(redacted).doesNotContain("username")
        assertThat(redacted).doesNotContain("password")
        assertThat(redacted).isEqualTo("https://<host>/…(1 segments)")
    }

    @Test
    fun `a url with no path is still reduced to its scheme`() {
        assertThat(Redact.url("https://panel.example.com")).isEqualTo("https://<host>/…(0 segments)")
    }

    @Test
    fun `a blank url is named rather than printed as empty`() {
        assertThat(Redact.url(null)).isEqualTo("<none>")
        assertThat(Redact.url("")).isEqualTo("<none>")
    }

    @Test
    fun `a secret leaks its length and nothing else`() {
        assertThat(Redact.secret("aVeryLongAccessToken")).isEqualTo("<redacted:20>")
        assertThat(Redact.secret("")).isEqualTo("<none>")
        assertThat(Redact.secret(null)).isEqualTo("<none>")
    }

    @Test
    fun `an email keeps its domain so support can act on it`() {
        assertThat(Redact.email("someone@example.com")).isEqualTo("s…e@example.com")
        // Two characters or fewer would be the whole local part.
        assertThat(Redact.email("ab@example.com")).isEqualTo("…@example.com")
        assertThat(Redact.email("not-an-email")).isEqualTo("<redacted>")
    }
}
