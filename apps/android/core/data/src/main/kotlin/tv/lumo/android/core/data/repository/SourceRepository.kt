package tv.lumo.android.core.data.repository

import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import tv.lumo.android.core.data.LumoResult
import tv.lumo.android.core.data.internal.ApiCaller
import tv.lumo.android.core.data.map
import tv.lumo.android.network.generated.api.SourcesApi
import tv.lumo.android.network.generated.model.CreateSourceRequest
import tv.lumo.android.network.generated.model.Source
import tv.lumo.android.network.generated.model.SourceKind
import tv.lumo.android.network.generated.model.UpdateSourceRequest

/**
 * The user's sources: registering one, watching it import, managing it.
 *
 * <h2>Why the generated `Source` is handed back as it is</h2>
 *
 * Unlike a channel, a source has exactly one origin — the server. It is not
 * cached in Room, nothing reshapes it, and its identifiers and dates are read by
 * screens rather than joined against anything local. Introducing a twin of it
 * here would buy nothing and cost the thing ADR 0001 is for: one description of
 * the shape, in `openapi.yaml`.
 *
 * The same reasoning gives the opposite answer for [tv.lumo.android.core.data.model.Channel],
 * which genuinely arrives as two types, and for
 * [tv.lumo.android.core.data.model.PlaybackTarget], whose generated form prints a
 * credential-bearing URL in `toString()`.
 *
 * <h2>Nothing here validates what the API validates</h2>
 *
 * Which fields a kind requires — `XTREAM` needs host, username and password;
 * `M3U_URL` needs a playlist URL — is enforced by the server, per field, with a
 * `VALIDATION_FAILED` carrying `errors[]`. A second copy of those rules on the
 * device would be one more place to forget the day the contract gains a kind,
 * and it would be the copy that is wrong.
 */
@Singleton
class SourceRepository @Inject internal constructor(
    private val api: SourcesApi,
    private val calls: ApiCaller,
) {

    suspend fun sources(): LumoResult<List<Source>> =
        calls.call { api.listSources() }.map { it.items }

    /**
     * One source, fresh.
     *
     * This is the call a screen polls while an import runs: `status` moves
     * `PENDING → SYNCING → READY`, and `sync_step` says which of the four stages
     * the server is on. The polling interval belongs to the screen — the server
     * has no push channel, and a repository that owned a timer would be a
     * repository nobody could test without one.
     */
    suspend fun source(id: String): LumoResult<Source> =
        calls.call { api.getSource(UUID.fromString(id)) }

    suspend fun create(
        label: String,
        kind: SourceKind,
        host: String? = null,
        username: String? = null,
        password: String? = null,
        m3uUrl: String? = null,
        epgUrl: String? = null,
    ): LumoResult<Source> = calls.call {
        api.createSource(
            CreateSourceRequest(
                label = label,
                kind = kind,
                host = host,
                username = username,
                password = password,
                m3uUrl = m3uUrl,
                epgUrl = epgUrl,
                // Not offered at creation, and on by default: a catalogue that
                // goes stale in silence is the failure a user cannot diagnose.
                // The source screen is where it can be turned off.
                autoSync = true,
            ),
        )
    }

    /**
     * Renames a source, or turns automatic re-synchronisation on and off.
     *
     * Neither touches host, credentials or URLs — changing one of those
     * re-triggers an ingestion, which is a different screen and a different
     * conversation with the user. `auto_sync` is the one property in this request
     * that leaves the catalogue alone.
     */
    suspend fun update(
        id: String,
        label: String? = null,
        autoSync: Boolean? = null,
    ): LumoResult<Source> = calls.call {
        api.updateSource(
            UUID.fromString(id),
            UpdateSourceRequest(label = label, autoSync = autoSync),
        )
    }

    /**
     * Asks for a re-synchronisation now.
     *
     * `SOURCE_SYNC_IN_PROGRESS` is a normal answer rather than an error worth
     * showing: it means the thing the user just asked for is already happening,
     * and the screen they are on is showing it happen.
     */
    suspend fun sync(id: String): LumoResult<Source> =
        calls.call { api.syncSource(UUID.fromString(id)) }

    /** Takes the source and, by cascade on the server, its whole catalogue. */
    suspend fun delete(id: String): LumoResult<Unit> =
        calls.empty { api.deleteSource(UUID.fromString(id)) }
}
