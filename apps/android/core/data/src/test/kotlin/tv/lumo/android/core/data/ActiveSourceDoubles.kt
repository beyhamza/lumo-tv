package tv.lumo.android.core.data

import java.util.UUID
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.Response
import tv.lumo.android.core.data.internal.ActiveSourceStore
import tv.lumo.android.network.generated.api.SourcesApi
import tv.lumo.android.network.generated.model.CreateSourceRequest
import tv.lumo.android.network.generated.model.Source
import tv.lumo.android.network.generated.model.SourceKind
import tv.lumo.android.network.generated.model.SourceList
import tv.lumo.android.network.generated.model.SourceStatus
import tv.lumo.android.network.generated.model.UpdateSourceRequest

/**
 * The doubles the active-source tests share.
 *
 * In one file for the reason `SessionDoubles.kt` gives: two file-private copies
 * of a name do not compile in one package, and two subtly different fakes are how
 * two tests come to disagree about what "the list failed" means.
 */

/** A bench source. The label is what assertions read; nothing here is a real one. */
internal fun benchSource(
    label: String,
    status: SourceStatus = SourceStatus.READY,
    id: UUID = UUID.nameUUIDFromBytes(label.toByteArray()),
) = Source(
    id = id,
    label = label,
    kind = SourceKind.M3U_URL,
    status = status,
    autoSync = true,
)

internal val Source.key: String get() = id.toString()

/**
 * `GET /sources`, from a list a test rewrites as the account changes — and a
 * switch that makes it fail, which is the half of US-018 that is awkward to
 * reproduce by hand.
 *
 * The failure is a `503`: not a timeout and not a `404`, because the rule under
 * test is that **nothing but** a proof of deletion may touch the choice, and a
 * `5xx` is the failure most easily mistaken for one.
 */
internal class ScriptedSourcesApi(
    var sources: List<Source> = emptyList(),
    var failing: Boolean = false,
    /**
     * `DELETE /sources/{id}`, for the one test that deletes (US-024). Null keeps
     * the call unreachable, which is what every other test here wants: they are
     * about a list, and a delete nobody scripted is a test gone wrong.
     */
    private val onDelete: ((UUID) -> Response<Unit>)? = null,
) : SourcesApi {

    var calls = 0
        private set

    override suspend fun listSources(): Response<SourceList> {
        calls++
        return if (failing) {
            Response.error(503, """{"code":"INTERNAL_ERROR"}""".toResponseBody(problemJson))
        } else {
            Response.success(SourceList(sources))
        }
    }

    override suspend fun createSource(createSourceRequest: CreateSourceRequest) = unreachable()

    override suspend fun deleteSource(id: UUID): Response<Unit> =
        onDelete?.invoke(id) ?: unreachable()

    override suspend fun getSource(id: UUID) = unreachable()

    override suspend fun syncSource(id: UUID) = unreachable()

    override suspend fun updateSource(
        id: UUID,
        updateSourceRequest: UpdateSourceRequest,
    ) = unreachable()

    private fun unreachable(): Nothing =
        throw AssertionError("the active source is decided from the list, and from nothing else")

    private companion object {
        val problemJson = "application/problem+json".toMediaType()
    }
}

/** The device's memory, in memory. One entry per account, like the real one. */
internal class InMemoryActiveSourceStore : ActiveSourceStore {

    val entries = mutableMapOf<String, String>()

    override suspend fun read(accountId: String): String? = entries[accountId]

    override suspend fun write(accountId: String, sourceId: String) {
        entries[accountId] = sourceId
    }

    override suspend fun clear(accountId: String) {
        entries.remove(accountId)
    }
}
