package tv.lumo.android.core.player.cast

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Test
import tv.lumo.android.core.player.AudioTrack
import tv.lumo.android.core.player.BackgroundPlayback
import tv.lumo.android.core.player.LumoPlayer
import tv.lumo.android.core.player.PlaybackProgress
import tv.lumo.android.core.player.PlaybackRequest
import tv.lumo.android.core.player.PlaybackState
import tv.lumo.android.core.player.SeekAvailability
import tv.lumo.android.core.player.WatchedSegment
import tv.lumo.android.core.player.WatchedTime

/**
 * S16-01/03/04/05: switching between the phone and a Chromecast, with fakes on both
 * ends. Fixture URLs only (AGENTS.md §1).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CastingLumoPlayerTest {

    private val film = PlaybackRequest(
        streamUrl = "https://bench.test/film/secret-token/a.mp4",
        title = "Film",
        isLive = false,
        artworkUrl = "https://bench.test/poster.jpg",
    )
    private val channel = PlaybackRequest(streamUrl = "https://bench.test/live/secret-token/1.ts", title = "Chaîne")

    // ---- without a receiver -------------------------------------------------

    @Test
    fun `without Cast the player is the phone's, untouched`() = runTest(UnconfinedTestDispatcher()) {
        val local = FakeLocal()
        val player = CastingLumoPlayer(local, remote = null, scope = backgroundScope)

        player.play(film)
        local.progress.value = PlaybackProgress(5_000, 60_000, SeekAvailability.AVAILABLE)

        assertThat(local.calls).containsExactly("play").inOrder()
        assertThat(player.progress.value.positionMs).isEqualTo(5_000)
        assertThat(player.castUi.value).isEqualTo(CastUi())
        assertThat(player.isRemote).isFalse()
    }

    // ---- S16-01: the button and the hand-over -------------------------------

    @Test
    fun `the button shows once a device is detected and the stream is known`() = runTest(UnconfinedTestDispatcher()) {
        val (player, _, remote) = setUp()
        assertThat(player.castUi.value.buttonVisible).isFalse()

        player.play(film)
        assertThat(player.castUi.value.buttonVisible).isFalse()

        remote.devicesAvailable.value = true
        assertThat(player.castUi.value.buttonVisible).isTrue()
    }

    @Test
    fun `a source with a User-Agent shows no button and is never sent`() = runTest(UnconfinedTestDispatcher()) {
        val (player, local, remote) = setUp()
        remote.devicesAvailable.value = true
        player.play(film.copy(needsUserAgent = true))
        assertThat(player.castUi.value.buttonVisible).isFalse()

        remote.connect()

        assertThat(remote.loads).isEmpty()
        assertThat(local.calls).containsExactly("play")
    }

    @Test
    fun `choosing a device stops the phone before the LOAD and resumes at the same position`() =
        runTest(UnconfinedTestDispatcher()) {
            val (player, local, remote) = setUp()
            player.play(film)
            local.progress.value = PlaybackProgress(61_000, 600_000, SeekAvailability.AVAILABLE)

            remote.connect()

            assertThat(local.calls).containsExactly("play", "stop").inOrder()
            assertThat(remote.events).containsExactly("load").inOrder()
            val (media, start) = remote.loads.single()
            assertThat(start).isEqualTo(61_000)
            assertThat(media.url).isEqualTo(film.streamUrl)
            assertThat(media.container).isEqualTo(CastContainer.MP4)
            assertThat(media.live).isFalse()
            assertThat(media.title).isEqualTo("Film")
            assertThat(media.artworkUrl).isEqualTo("https://bench.test/poster.jpg")
            assertThat(player.castUi.value.casting).isTrue()
            assertThat(player.castUi.value.deviceName).isEqualTo("Salon")
            assertThat(player.isRemote).isTrue()
            assertThat(player.state.value).isEqualTo(PlaybackState.Buffering)

            remote.status.value = RemoteStatus(RemotePlayerState.PLAYING, positionMs = 62_000, durationMs = 600_000, seekable = true, volume = 0.4f)

            assertThat(player.state.value).isEqualTo(PlaybackState.Playing("Film"))
            assertThat(player.progress.value).isEqualTo(PlaybackProgress(62_000, 600_000, SeekAvailability.AVAILABLE))
            assertThat(player.castUi.value.volume).isEqualTo(0.4f)
        }

    @Test
    fun `a channel is sent live, from its edge`() = runTest(UnconfinedTestDispatcher()) {
        val (player, _, remote) = setUp()
        player.play(channel)

        remote.connect()
        remote.status.value = RemoteStatus(RemotePlayerState.PLAYING, positionMs = 9_999)

        val (media, start) = remote.loads.single()
        assertThat(media.live).isTrue()
        assertThat(media.container).isEqualTo(CastContainer.MPEG_TS)
        assertThat(start).isEqualTo(0)
        assertThat(player.progress.value.seek).isEqualTo(SeekAvailability.LIVE)
    }

    @Test
    fun `with a device already connected, the next stream goes straight to it`() = runTest(UnconfinedTestDispatcher()) {
        val (player, local, remote) = setUp()
        remote.connect()

        player.play(film)

        assertThat(local.calls).containsExactly("stop")
        assertThat(remote.loads).hasSize(1)
    }

    @Test
    fun `a film whose server ignores Range starts at zero on the receiver, and says so`() =
        runTest(UnconfinedTestDispatcher()) {
            val (player, local, remote) = setUp()
            player.play(film)
            local.progress.value = PlaybackProgress(61_000, 600_000, SeekAvailability.REFUSED)

            remote.connect()
            remote.status.value = RemoteStatus(RemotePlayerState.PLAYING, positionMs = 1_000, seekable = true)

            assertThat(remote.loads.single().second).isEqualTo(0)
            assertThat(player.castUi.value.notice).isEqualTo(CastNotice.STARTED_FROM_BEGINNING)
            assertThat(player.progress.value.seek).isEqualTo(SeekAvailability.REFUSED)
            player.seekTo(30_000)
            assertThat(remote.events).doesNotContain("seek")
        }

    // ---- S16-03: the remote control ------------------------------------------

    @Test
    fun `play, pause, seek and volume go to the receiver while casting`() = runTest(UnconfinedTestDispatcher()) {
        val (player, local, remote) = casting(this)
        remote.status.value = RemoteStatus(RemotePlayerState.PLAYING, positionMs = 1_000, durationMs = 600_000, seekable = true)

        player.pause()
        player.resume()
        player.seekTo(120_000)
        player.setVolume(1.5f)

        assertThat(remote.events).containsAtLeast("pause", "play", "seek:120000", "volume:1.0").inOrder()
        assertThat(local.calls).doesNotContain("pause")
        assertThat(player.progress.value.positionMs).isEqualTo(120_000)
    }

    @Test
    fun `leaving the app while casting does not pause the television`() = runTest(UnconfinedTestDispatcher()) {
        val (player, _, remote) = casting(this)

        assertThat(BackgroundPlayback(player).onBackground()).isFalse()
        assertThat(remote.events).doesNotContain("pause")
    }

    @Test
    fun `stopping the cast keeps the position and offers it back to the phone`() = runTest(UnconfinedTestDispatcher()) {
        val (player, local, remote) = casting(this)
        remote.status.value = RemoteStatus(RemotePlayerState.PLAYING, positionMs = 300_000, durationMs = 600_000, seekable = true)
        local.calls.clear()

        player.stopCasting()

        assertThat(remote.events).contains("endSession")
        assertThat(player.isRemote).isFalse()
        assertThat(player.state.value).isEqualTo(PlaybackState.Paused)
        assertThat(player.progress.value.positionMs).isEqualTo(300_000)
        assertThat(player.castUi.value.resumeOffer).isEqualTo(CastResumeOffer(300_000))
        assertThat(local.calls).isEmpty()

        player.resumeHere()
        local.progress.value = PlaybackProgress(0, 600_000, SeekAvailability.AVAILABLE)

        assertThat(local.calls).containsExactly("play", "seek:300000").inOrder()
        assertThat(player.castUi.value.resumeOffer).isNull()
    }

    @Test
    fun `play after a stopped cast is resume here`() = runTest(UnconfinedTestDispatcher()) {
        val (player, local, remote) = casting(this)
        remote.status.value = RemoteStatus(RemotePlayerState.PLAYING, positionMs = 10_000, seekable = true)
        local.calls.clear()
        remote.disconnect()

        assertThat(player.castUi.value.resumeOffer).isEqualTo(CastResumeOffer(10_000))
        player.resume()

        assertThat(local.calls).contains("play")
    }

    @Test
    fun `a channel's offer carries no position`() = runTest(UnconfinedTestDispatcher()) {
        val (player, _, remote) = setUp()
        player.play(channel)
        remote.connect()
        remote.status.value = RemoteStatus(RemotePlayerState.PLAYING)

        player.stopCasting()

        assertThat(player.castUi.value.resumeOffer).isEqualTo(CastResumeOffer(null))
    }

    @Test
    fun `leaving the screen stops the receiver and drops the stream`() = runTest(UnconfinedTestDispatcher()) {
        val (player, local, remote) = casting(this)

        player.stop()

        assertThat(remote.events).contains("stopMedia")
        assertThat(local.calls).contains("stop")
        assertThat(player.castUi.value.casting).isFalse()
        assertThat(player.castUi.value.buttonVisible).isFalse()
    }

    // ---- S16-04: progress, watched time, the next episode --------------------

    @Test
    fun `watched time counts only while the receiver plays`() = runTest(UnconfinedTestDispatcher()) {
        val (player, _, remote) = casting(this)

        remote.status.value = RemoteStatus(RemotePlayerState.BUFFERING)
        advanceTimeBy(5_000)
        remote.status.value = RemoteStatus(RemotePlayerState.PLAYING, positionMs = 1_000)
        advanceTimeBy(10_000)
        remote.status.value = RemoteStatus(RemotePlayerState.PAUSED, positionMs = 11_000)
        advanceTimeBy(7_000)

        val watched = player.takeWatched()
        assertThat(watched.segments).containsExactly(WatchedSegment(5_000, 15_000))
        assertThat(watched.sessionStartedAtMs).isEqualTo(5_000)
    }

    @Test
    fun `time watched on the phone before the hand-over is not lost`() = runTest(UnconfinedTestDispatcher()) {
        val (player, local, remote) = setUp()
        player.play(film)
        local.watched = WatchedTime(listOf(WatchedSegment(0, 4_000)), sessionStartedAtMs = 0)

        remote.connect()
        advanceTimeBy(5_000)
        remote.status.value = RemoteStatus(RemotePlayerState.PLAYING)
        advanceTimeBy(2_000)

        val watched = player.takeWatched()
        assertThat(watched.segments).containsExactly(WatchedSegment(0, 4_000), WatchedSegment(5_000, 7_000)).inOrder()
        assertThat(watched.sessionStartedAtMs).isEqualTo(0)
    }

    @Test
    fun `the end on the receiver is Ended, and the next episode is sent there`() = runTest(UnconfinedTestDispatcher()) {
        val (player, local, remote) = casting(this)
        remote.status.value = RemoteStatus(RemotePlayerState.PLAYING, positionMs = 590_000, durationMs = 600_000, seekable = true)

        remote.status.value = RemoteStatus(RemotePlayerState.IDLE, idleReason = RemoteIdleReason.FINISHED)

        assertThat(player.state.value).isEqualTo(PlaybackState.Ended)
        assertThat(player.progress.value.positionMs).isEqualTo(600_000)

        player.play(film.copy(title = "Épisode 2"))

        assertThat(remote.loads).hasSize(2)
        assertThat(remote.loads.last().first.title).isEqualTo("Épisode 2")
        assertThat(local.calls).doesNotContain("play")
    }

    // ---- S16-05: refusals ----------------------------------------------------

    @Test
    fun `a refused LOAD goes back to the phone at the position reached, said once, URL never logged`() =
        runTest(UnconfinedTestDispatcher()) {
            val logs = mutableListOf<String>()
            val (player, local, remote) = setUp(log = logs)
            remote.nextLoadCode = 2100
            player.play(film)
            local.progress.value = PlaybackProgress(61_000, 600_000, SeekAvailability.AVAILABLE)

            remote.connect()
            local.progress.value = PlaybackProgress(0, 600_000, SeekAvailability.AVAILABLE)

            assertThat(local.calls).containsExactly("play", "stop", "play", "seek:61000").inOrder()
            assertThat(player.castUi.value.notice).isEqualTo(CastNotice.REFUSED)
            assertThat(player.isRemote).isFalse()
            assertThat(remote.events).contains("endSession")
            assertThat(logs).containsExactly("cast refused: load:2100")
            assertThat(logs.joinToString()).doesNotContain("secret-token")
        }

    @Test
    fun `a receiver error after a start resumes the phone where the receiver was`() = runTest(UnconfinedTestDispatcher()) {
        val (player, local, remote) = casting(this)
        remote.status.value = RemoteStatus(RemotePlayerState.PLAYING, positionMs = 120_000, seekable = true)
        local.calls.clear()

        remote.signals.emit(CastSignal.MediaError(104, "LOAD_FAILED"))
        local.progress.value = PlaybackProgress(0, 600_000, SeekAvailability.AVAILABLE)

        assertThat(local.calls).containsExactly("play", "seek:120000").inOrder()
        assertThat(player.castUi.value.notice).isEqualTo(CastNotice.REFUSED)
    }

    @Test
    fun `idle on error is a refusal`() = runTest(UnconfinedTestDispatcher()) {
        val (player, _, remote) = casting(this)
        remote.status.value = RemoteStatus(RemotePlayerState.LOADING)

        remote.status.value = RemoteStatus(RemotePlayerState.IDLE, idleReason = RemoteIdleReason.ERROR)

        assertThat(player.castUi.value.notice).isEqualTo(CastNotice.REFUSED)
        assertThat(player.isRemote).isFalse()
    }

    @Test
    fun `fifteen seconds without playing is a refusal`() = runTest(UnconfinedTestDispatcher()) {
        val (player, _, remote) = casting(this)
        remote.status.value = RemoteStatus(RemotePlayerState.BUFFERING)

        advanceTimeBy(14_999)
        assertThat(player.isRemote).isTrue()
        advanceTimeBy(2)

        assertThat(player.castUi.value.notice).isEqualTo(CastNotice.REFUSED)
        assertThat(player.isRemote).isFalse()
    }

    @Test
    fun `904 and a cancelled LOAD are not refusals`() = runTest(UnconfinedTestDispatcher()) {
        val (player, _, remote) = casting(this)

        remote.signals.emit(CastSignal.MediaError(904, "ERROR"))
        remote.status.value = RemoteStatus(RemotePlayerState.PLAYING)
        advanceTimeBy(20_000)

        assertThat(player.isRemote).isTrue()
        assertThat(player.castUi.value.notice).isNull()
    }

    @Test
    fun `the picker closed without a choice leaves the phone playing`() = runTest(UnconfinedTestDispatcher()) {
        val (player, local, remote) = setUp()
        player.play(film)

        remote.session.value = RemoteSession.Connecting
        remote.session.value = RemoteSession.None
        remote.signals.emit(CastSignal.SessionStartFailed(CastSignals.CANCELED))

        assertThat(local.calls).containsExactly("play")
        assertThat(player.castUi.value.notice).isNull()
    }

    @Test
    fun `the previous stream's idle status is not read as this one's`() = runTest(UnconfinedTestDispatcher()) {
        val (player, _, remote) = setUp()
        remote.status.value = RemoteStatus(RemotePlayerState.IDLE, idleReason = RemoteIdleReason.ERROR)
        remote.connect()

        player.play(film)

        assertThat(player.isRemote).isTrue()
        assertThat(player.castUi.value.notice).isNull()
    }

    // ---- fixtures -------------------------------------------------------------

    private data class Rig(val player: CastingLumoPlayer, val local: FakeLocal, val remote: FakeRemote)

    private fun TestScope.setUp(log: MutableList<String>? = null): Rig {
        val local = FakeLocal()
        val remote = FakeRemote()
        val player = CastingLumoPlayer(
            local = local,
            remote = remote,
            scope = backgroundScope,
            clock = { testScheduler.currentTime },
            log = { log?.add(it) },
        )
        return Rig(player, local, remote)
    }

    /** A film already on the receiver, accepted, before any status. */
    private fun casting(scope: TestScope): Rig = scope.setUp().also { (player, _, remote) ->
        remote.connect()
        player.play(film)
        remote.events.clear()
    }

    private class FakeLocal : LumoPlayer {
        val calls = mutableListOf<String>()
        var watched = WatchedTime.NONE
        override val state = MutableStateFlow<PlaybackState>(PlaybackState.Idle)
        override val progress = MutableStateFlow(PlaybackProgress())
        override val audioTracks = MutableStateFlow<List<AudioTrack>>(emptyList())
        override fun play(request: PlaybackRequest) {
            calls += "play"
            state.value = PlaybackState.Buffering
        }
        override fun selectAudioTrack(id: String) = Unit
        override fun seekTo(positionMs: Long) { calls += "seek:$positionMs" }
        override fun pause() { calls += "pause" }
        override fun resume() { calls += "resume" }
        override fun takeWatched(): WatchedTime = watched.also { watched = WatchedTime.NONE }
        override fun stop() {
            calls += "stop"
            state.value = PlaybackState.Idle
            progress.value = PlaybackProgress()
        }
        override fun release() = Unit
    }

    private class FakeRemote : RemotePlayback {
        val events = mutableListOf<String>()
        val loads = mutableListOf<Pair<RemoteMedia, Long>>()
        var nextLoadCode = CastSignals.SUCCESS
        override val supported = MutableStateFlow(true)
        override val devicesAvailable = MutableStateFlow(false)
        override val session = MutableStateFlow<RemoteSession>(RemoteSession.None)
        override val status = MutableStateFlow<RemoteStatus?>(null)
        override val signals = MutableSharedFlow<CastSignal>(extraBufferCapacity = 8)

        fun connect() {
            devicesAvailable.value = true
            session.value = RemoteSession.Connected("Salon")
        }

        fun disconnect() {
            session.value = RemoteSession.None
            status.value = null
        }

        override suspend fun load(media: RemoteMedia, startPositionMs: Long): Int {
            events += "load"
            loads += media to startPositionMs
            return nextLoadCode
        }
        override fun play() { events += "play" }
        override fun pause() { events += "pause" }
        override fun seekTo(positionMs: Long) { events += "seek:$positionMs" }
        override fun setVolume(level: Float) { events += "volume:$level" }
        override fun stopMedia() { events += "stopMedia" }
        override fun endSession() {
            events += "endSession"
            disconnect()
        }
    }
}
