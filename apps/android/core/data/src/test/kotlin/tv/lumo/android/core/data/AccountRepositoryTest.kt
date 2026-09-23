package tv.lumo.android.core.data

import com.google.common.truth.Truth.assertThat
import java.util.UUID
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import tv.lumo.android.core.auth.SessionManager
import tv.lumo.android.core.data.internal.ApiCaller
import tv.lumo.android.core.data.internal.ProblemReader
import tv.lumo.android.core.data.repository.AccountRepository
import tv.lumo.android.network.generated.api.AccountApi
import tv.lumo.android.network.generated.api.AuthApi
import tv.lumo.android.network.generated.infrastructure.Serializer
import tv.lumo.android.network.generated.model.ErrorCode
import tv.lumo.android.network.generated.model.Platform

/**
 * The devices of the account (US-025, S8-06), against a real HTTP stack.
 *
 * Two things the settings screen depends on and cannot see for itself: that
 * `DELETE /me/devices/{id}` names the device it was asked about and answers a
 * bare success on `204`, and that a device already removed elsewhere comes back
 * as `DEVICE_NOT_FOUND` rather than as something a screen would call "try
 * again" — the design keeps the device on a failure, and this is the failure
 * that must not.
 */
class AccountRepositoryTest {

    private lateinit var server: MockWebServer
    private lateinit var retrofit: Retrofit

    @Before
    fun start() {
        server = MockWebServer()
        server.start()
        retrofit = Retrofit.Builder()
            .baseUrl(server.url("/").toString())
            .addConverterFactory(MoshiConverterFactory.create(Serializer.moshiBuilder.build()))
            .build()
    }

    @After
    fun stop() = server.shutdown()

    @Test
    fun `the device list is read as the server orders it, current flag included`() = runBlocking {
        val here = UUID.randomUUID()
        val there = UUID.randomUUID()
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {"items":[
                  ${device(here, Platform.ANDROID_MOBILE, current = true)},
                  ${device(there, Platform.ANDROID_TV, current = false)}
                ]}
                """.trimIndent(),
            ),
        )

        val result = repository().devices()

        val devices = (result as LumoResult.Success).value
        assertThat(devices.map { it.id }).containsExactly(here, there).inOrder()
        // `is_current` is the server's answer, computed against the token that
        // made the call: the screen marks that row and never guesses it.
        assertThat(devices.single { it.isCurrent }.id).isEqualTo(here)
        assertThat(devices.first { it.id == there }.lastSeenAt).isNull()
    }

    @Test
    fun `revoking a device names it on the wire and answers a bare success`() = runBlocking {
        val id = UUID.randomUUID()
        server.enqueue(MockResponse().setResponseCode(204))

        val result = repository().revokeDevice(id.toString())

        assertThat(result).isEqualTo(LumoResult.Success(Unit))
        val request = server.takeRequest()
        assertThat(request.method).isEqualTo("DELETE")
        assertThat(request.path).isEqualTo("/me/devices/$id")
    }

    @Test
    fun `a device already removed is a known refusal, not an unreadable one`() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(404)
                .setHeader("Content-Type", "application/problem+json")
                .setBody("""{"code":"DEVICE_NOT_FOUND","detail":"No such device"}"""),
        )

        val result = repository().revokeDevice(UUID.randomUUID().toString())

        val error = (result as LumoResult.Failure).error
        assertThat(error).isInstanceOf(LumoError.Api::class.java)
        assertThat((error as LumoError.Api).code).isEqualTo(ErrorCode.DEVICE_NOT_FOUND)
    }

    // ---- helpers -----------------------------------------------------------

    private fun repository() = AccountRepository(
        account = retrofit.create(AccountApi::class.java),
        auth = retrofit.create(AuthApi::class.java),
        calls = ApiCaller(ProblemReader(Serializer.moshiBuilder.build())),
        session = SessionManager(FakeSessionStore(), RejectingRefresher),
    )

    private fun device(id: UUID, platform: Platform, current: Boolean): String = """
        {
          "id": "$id",
          "platform": "${platform.value}",
          "name": null,
          "model": "Bench",
          "app_version": "0.1.0",
          "last_seen_at": null,
          "is_current": $current,
          "created_at": "2026-09-01T10:00:00Z"
        }
    """.trimIndent()
}
