package tv.lumo.android.core.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import tv.lumo.android.core.common.playback.TrackPreferences

/**
 * Playback preferences, local to this device and never synchronised (S13-02/03/05, Q6).
 *
 * <h2>Two scopes in one file</h2>
 *
 * - **Per account on this device**: audio language, subtitles on/off and their
 *   language, next episode automatically. Account A's choices are never applied
 *   to account B, and A finds them again on coming back (PS-02).
 * - **Per installation**: playback on mobile data (Q7). Kept across a change of
 *   account — it is about the phone's data plan, not about a person.
 *
 * <h2>Initial values (Q6)</h2>
 *
 * The first time an account plays something here, its audio and subtitle
 * languages are seeded from the interface language and **written**, so changing
 * the interface language later does not move them (PS-01). Subtitles start off,
 * the next episode starts automatically, mobile data is allowed.
 *
 * <h2>Writes say whether they happened</h2>
 *
 * Every setter returns false when the file could not be written, so a screen can
 * say "applied for this playback, preference not saved" instead of announcing a
 * saved preference that is not (PS-07).
 */
interface PlaybackPreferencesRepository {

    /** The settings screen's view: next episode and mobile data, for the current account. */
    val settings: Flow<PlaybackSettings>

    /** The languages to open the next stream with, seeding them on first use. */
    suspend fun trackPreferences(): TrackPreferences

    suspend fun saveTrackPreferences(preferences: TrackPreferences): Boolean

    suspend fun autoplayNext(): Boolean

    suspend fun setAutoplayNext(enabled: Boolean): Boolean

    /** Android mobile only; a television never reads it. */
    suspend fun mobileDataAllowed(): Boolean

    val mobileDataAllowedChanges: Flow<Boolean>

    suspend fun setMobileDataAllowed(allowed: Boolean): Boolean
}

data class PlaybackSettings(
    val autoplayNext: Boolean = true,
    val mobileDataAllowed: Boolean = true,
)

/**
 * [PlaybackPreferencesRepository] over one Preferences DataStore.
 *
 * @param accountId the signed-in account, or null. Without one nothing is
 *   written and the defaults are answered: a preference belongs to somebody.
 * @param interfaceLanguage the language the interface is shown in, read at the
 *   moment of the first seeding only.
 */
internal class DataStorePlaybackPreferences(
    private val preferences: DataStore<Preferences>,
    private val accountId: suspend () -> String?,
    accountIds: Flow<String?>,
    private val interfaceLanguage: () -> String?,
) : PlaybackPreferencesRepository {

    private val data: Flow<Preferences> = preferences.data
        .catch { cause -> if (cause is IOException) emit(emptyPreferences()) else throw cause }

    override val settings: Flow<PlaybackSettings> = combine(data, accountIds) { stored, account ->
        PlaybackSettings(
            autoplayNext = account?.let { stored[autoplayKey(it)] } ?: true,
            mobileDataAllowed = stored[MOBILE_DATA_ALLOWED] ?: true,
        )
    }

    override val mobileDataAllowedChanges: Flow<Boolean> =
        combine(data, accountIds) { stored, _ -> stored[MOBILE_DATA_ALLOWED] ?: true }

    override suspend fun trackPreferences(): TrackPreferences {
        val account = accountId() ?: return TrackPreferences.NONE
        val stored = data.first()
        if (stored[seededKey(account)] == true) {
            return TrackPreferences(
                audioLanguage = stored[audioKey(account)],
                subtitlesEnabled = stored[subtitlesOnKey(account)] ?: false,
                subtitleLanguage = stored[subtitleLanguageKey(account)],
            )
        }

        // First use on this device: the interface language, subtitles off (Q6).
        val language = interfaceLanguage()?.takeIf { it.isNotBlank() }
        val seeded = TrackPreferences(audioLanguage = language, subtitleLanguage = language)
        saveTrackPreferences(seeded)
        return seeded
    }

    override suspend fun saveTrackPreferences(preferences: TrackPreferences): Boolean {
        val account = accountId() ?: return false
        return write {
            it[seededKey(account)] = true
            it.setOrRemove(audioKey(account), preferences.audioLanguage)
            it[subtitlesOnKey(account)] = preferences.subtitlesEnabled
            it.setOrRemove(subtitleLanguageKey(account), preferences.subtitleLanguage)
        }
    }

    override suspend fun autoplayNext(): Boolean {
        val account = accountId() ?: return true
        return data.first()[autoplayKey(account)] ?: true
    }

    override suspend fun setAutoplayNext(enabled: Boolean): Boolean {
        val account = accountId() ?: return false
        return write { it[autoplayKey(account)] = enabled }
    }

    override suspend fun mobileDataAllowed(): Boolean = data.first()[MOBILE_DATA_ALLOWED] ?: true

    override suspend fun setMobileDataAllowed(allowed: Boolean): Boolean =
        write { it[MOBILE_DATA_ALLOWED] = allowed }

    private suspend fun write(change: (androidx.datastore.preferences.core.MutablePreferences) -> Unit): Boolean =
        try {
            preferences.edit(change)
            true
        } catch (_: IOException) {
            false
        }

    private fun androidx.datastore.preferences.core.MutablePreferences.setOrRemove(
        key: Preferences.Key<String>,
        value: String?,
    ) {
        if (value == null) remove(key) else set(key, value)
    }

    private companion object {
        val MOBILE_DATA_ALLOWED = booleanPreferencesKey("mobile_data_allowed")

        fun seededKey(account: String) = booleanPreferencesKey("$account.seeded")
        fun audioKey(account: String) = stringPreferencesKey("$account.audio_language")
        fun subtitlesOnKey(account: String) = booleanPreferencesKey("$account.subtitles_enabled")
        fun subtitleLanguageKey(account: String) = stringPreferencesKey("$account.subtitle_language")
        fun autoplayKey(account: String) = booleanPreferencesKey("$account.autoplay_next")
    }
}
