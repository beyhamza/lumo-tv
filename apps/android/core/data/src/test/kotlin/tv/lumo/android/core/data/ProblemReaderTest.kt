package tv.lumo.android.core.data

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import tv.lumo.android.core.data.internal.ProblemReader
import tv.lumo.android.network.generated.infrastructure.Serializer
import tv.lumo.android.network.generated.model.ErrorCode
import tv.lumo.android.network.generated.model.IngestionErrorCode

/**
 * The one translation of an API error, under the conditions it will actually
 * meet: a code this build knows, a code it does not, and a body that is not the
 * document the contract promised.
 *
 * The reader is built on the same Moshi the applications use — the generated
 * `Serializer` — rather than on a bare instance, so this test would notice the
 * day that configuration stops being able to read a problem document.
 */
class ProblemReaderTest {

    private val reader = ProblemReader(Serializer.moshiBuilder.build())

    @Test
    fun `every ingestion code survives the round trip`() {
        // The contract is explicit that no client may fall back to a generic
        // message on this surface: the user's next action is completely
        // different between "the server is down" and "your password is wrong".
        // So every one of them is checked, by enumeration rather than by sample.
        IngestionErrorCode.entries.forEach { ingestion ->
            val error = reader.read(status = 422, body = problem(ingestion.value))

            assertThat(error).isInstanceOf(LumoError.Api::class.java)
            assertThat((error as LumoError.Api).code.value).isEqualTo(ingestion.value)
        }
    }

    @Test
    fun `a known code becomes the generated enum, with its detail`() {
        val error = reader.read(409, problem("SOURCE_MAX_CONNECTIONS", detail = "3 in use"))

        assertThat(error).isEqualTo(
            LumoError.Api(ErrorCode.SOURCE_MAX_CONNECTIONS, detail = "3 in use"),
        )
    }

    @Test
    fun `a code added after this build shipped is reported, not lost`() {
        // The contract says new codes may appear within v1. This is the case the
        // generated `Problem` model cannot express at all: its `code` is a
        // non-null enum, so Moshi would throw and the reason would be gone.
        val error = reader.read(409, problem("SOURCE_SUSPENDED_BY_PROVIDER"))

        assertThat(error).isEqualTo(LumoError.UnknownCode("SOURCE_SUSPENDED_BY_PROVIDER"))
    }

    @Test
    fun `field errors come back keyed by input name`() {
        val body = """
            {
              "type": "https://lumo.tv/problems/validation-failed",
              "title": "Validation failed",
              "status": 422,
              "code": "VALIDATION_FAILED",
              "errors": [
                { "field": "/m3u_url", "code": "REQUIRED" },
                { "field": "/host", "code": "UNSUPPORTED", "detail": "not for M3U_URL" }
              ]
            }
        """.trimIndent()

        val error = reader.read(422, body) as LumoError.Api

        assertThat(error.code).isEqualTo(ErrorCode.VALIDATION_FAILED)
        // The pointer is `/m3u_url` and the input is `m3u_url`. The forms are
        // built from the contract's property names precisely so this is a
        // prefix removal and not a table somebody has to maintain.
        assertThat(error.fields.map { it.name }).containsExactly("m3u_url", "host")
        assertThat(error.fields.map { it.code }).containsExactly("REQUIRED", "UNSUPPORTED")
    }

    @Test
    fun `a malformed entry in errors is dropped, not guessed at`() {
        val body = """
            {
              "code": "VALIDATION_FAILED",
              "errors": [ { "field": "/label" }, { "field": "/host", "code": "REQUIRED" } ]
            }
        """.trimIndent()

        val error = reader.read(422, body) as LumoError.Api

        // A message rendered next to the wrong input is worse than no message:
        // the form-level one still reaches the user either way.
        assertThat(error.fields.map { it.name }).containsExactly("host")
    }

    @Test
    fun `a body that is not a problem document is unreadable, with its status`() {
        // What a proxy or a captive portal returns, and it is not JSON at all.
        val error = reader.read(502, "<html><body>Bad gateway</body></html>")

        assertThat(error).isInstanceOf(LumoError.Unreadable::class.java)
        assertThat((error as LumoError.Unreadable).status).isEqualTo(502)
    }

    @Test
    fun `valid JSON with no code is unreadable rather than a wrong guess`() {
        val error = reader.read(500, """{ "message": "boom" }""")

        assertThat(error).isEqualTo(LumoError.Unreadable(500, null))
    }

    @Test
    fun `an empty body is unreadable`() {
        assertThat(reader.read(503, null)).isEqualTo(LumoError.Unreadable(503, null))
        assertThat(reader.read(503, "   ")).isEqualTo(LumoError.Unreadable(503, null))
    }

    private fun problem(code: String, detail: String? = null): String {
        val detailField = detail?.let { ""","detail":"$it"""" } ?: ""
        return """
            {"type":"https://lumo.tv/problems/x","title":"x","status":409,
             "code":"$code"$detailField}
        """.trimIndent()
    }
}
