package tv.lumo.android.feature.live

import com.google.common.truth.Truth.assertThat
import java.time.Duration
import java.time.Instant
import org.junit.Test
import tv.lumo.android.core.data.EpgFreshness
import tv.lumo.android.core.data.LumoError
import tv.lumo.android.core.data.model.DataOrigin
import tv.lumo.android.core.data.model.EpgProgramme

/**
 * The Guide's data states: absence, load, error and emptiness (S9-06-03,
 * GD-10/GD-11).
 *
 * <h2>The one rule this file exists to pin</h2>
 *
 * "A failed read" and "a guide with nothing on" are not the same sentence, and
 * the difference cannot be decided at the drawing site from a list length: the
 * repository emits the **cache first**, so a cold start fills the answered set
 * with empty programme lists before the server has spoken. A grid that reads
 * "answered" as "empty" therefore announces "aucun programme" on a load that has
 * not finished — and keeps announcing it when the server never answers. That is
 * the GD-10 defect, and [guideStateOf] is the single place its rule lives: the
 * screens only render what it returns, exactly as `sheetFocusAfter` is the one
 * place GD-07's focus rule lives (D1 of S9-06-01).
 */
class GuideStatesTest {

    private val offline = LumoError.Offline(java.io.IOException("no route"))

    // ---- the three states GD-10 distinguishes ------------------------------

    @Test
    fun `an initial failure with no data is an initial error, not an empty guide`() {
        val status = GuideStatus(read = GuideRead.Failed(offline))

        val state = guideStateOf(
            configured = true,
            status = status,
            // The cache answered with nothing before the server failed: the
            // answered set is full, and it still means "no data".
            answered = setOf("c1", "c2"),
            programmes = emptyMap(),
        )

        assertThat(state).isEqualTo(GuideState.InitialError)
        assertThat(state).isNotEqualTo(GuideState.Empty)
    }

    @Test
    fun `a failure with programmes on screen is a data error`() {
        val status = GuideStatus(read = GuideRead.Failed(offline))

        val state = guideStateOf(
            configured = true,
            status = status,
            answered = setOf("c1"),
            programmes = mapOf("c1" to listOf(programme("p1"))),
        )

        assertThat(state).isEqualTo(GuideState.DataError)
        // Distinct from the initial error: the screen words the two differently.
        assertThat(state).isNotEqualTo(GuideState.InitialError)
    }

    @Test
    fun `a cache answer with nothing yet is a load, never an empty guide`() {
        val state = guideStateOf(
            configured = true,
            status = GuideStatus(read = GuideRead.FromCache),
            answered = setOf("c1", "c2"),
            programmes = emptyMap(),
        )

        assertThat(state).isEqualTo(GuideState.Loading)
    }

    @Test
    fun `before anything is read nothing is announced`() {
        val state = guideStateOf(
            configured = null,
            status = GuideStatus(),
            answered = emptySet(),
            programmes = emptyMap(),
        )

        assertThat(state).isEqualTo(GuideState.Loading)
    }

    // ---- an answer, with and without programmes ----------------------------

    @Test
    fun `a completed read with nothing over the day is empty`() {
        val state = guideStateOf(
            configured = true,
            status = GuideStatus(read = GuideRead.Complete),
            answered = setOf("c1", "c2"),
            programmes = mapOf("c1" to emptyList(), "c2" to emptyList()),
        )

        assertThat(state).isEqualTo(GuideState.Empty)
    }

    @Test
    fun `a completed read with programmes is content`() {
        val state = guideStateOf(
            configured = true,
            status = GuideStatus(read = GuideRead.Complete),
            answered = setOf("c1"),
            programmes = mapOf("c1" to listOf(programme("p1"))),
        )

        assertThat(state).isEqualTo(GuideState.Content)
    }

    @Test
    fun `cached programmes are content even before the server answers`() {
        val state = guideStateOf(
            configured = true,
            status = GuideStatus(read = GuideRead.FromCache),
            answered = setOf("c1"),
            programmes = mapOf("c1" to listOf(programme("p1"))),
        )

        assertThat(state).isEqualTo(GuideState.Content)
    }

    // ---- a source with no guide at all -------------------------------------

    @Test
    fun `a source with no guide configured is not a failure and not empty`() {
        val state = guideStateOf(
            configured = false,
            status = GuideStatus(read = GuideRead.Failed(offline)),
            answered = emptySet(),
            programmes = emptyMap(),
        )

        assertThat(state).isEqualTo(GuideState.NotConfigured)
    }

    // ---- a row: a skeleton is not an answered, empty row -------------------

    @Test
    fun `a row with cached programmes is answered before the server speaks`() {
        val answered = guideRowAnswered(
            channelId = "c1",
            status = GuideStatus(read = GuideRead.FromCache),
            answered = setOf("c1"),
            programmes = mapOf("c1" to listOf(programme("p1"))),
        )

        assertThat(answered).isTrue()
    }

    @Test
    fun `a cache row with nothing yet stays a skeleton`() {
        val answered = guideRowAnswered(
            channelId = "c1",
            status = GuideStatus(read = GuideRead.FromCache),
            answered = setOf("c1"),
            programmes = emptyMap(),
        )

        assertThat(answered).isFalse()
    }

    @Test
    fun `once the read is complete an empty row is a real answer`() {
        val answered = guideRowAnswered(
            channelId = "c1",
            status = GuideStatus(read = GuideRead.Complete),
            answered = setOf("c1"),
            programmes = mapOf("c1" to emptyList()),
        )

        assertThat(answered).isTrue()
    }

    @Test
    fun `a channel the read never asked for stays a skeleton`() {
        val answered = guideRowAnswered(
            channelId = "c9",
            status = GuideStatus(read = GuideRead.Complete),
            answered = setOf("c1"),
            programmes = emptyMap(),
        )

        assertThat(answered).isFalse()
    }

    // ---- age: the rule is EpgFreshness's, this only picks what to say ------

    @Test
    fun `a stale guide dates itself from the last import`() {
        val at = Instant.parse("2026-09-24T08:00:00Z")
        val status = GuideStatus(
            read = GuideRead.Complete,
            freshness = EpgFreshness.Stale(Duration.ofHours(26)),
            lastImportAt = at,
        )

        assertThat(guideAgeOf(status)).isEqualTo(GuideAge.LastImport(at))
    }

    @Test
    fun `a failed import is said as incomplete, without inventing a date`() {
        val status = GuideStatus(
            read = GuideRead.Complete,
            freshness = EpgFreshness.AttemptFailed(lastSuccessfulImportAt = null),
        )

        assertThat(guideAgeOf(status)).isEqualTo(GuideAge.Incomplete)
    }

    @Test
    fun `a fresh guide has nothing to say about its age`() {
        val status = GuideStatus(
            read = GuideRead.Complete,
            freshness = EpgFreshness.Fresh(Duration.ofHours(2)),
            lastImportAt = Instant.parse("2026-09-26T08:00:00Z"),
        )

        assertThat(guideAgeOf(status)).isNull()
    }

    @Test
    fun `a guide that was never dated has nothing to say about its age`() {
        val status = GuideStatus(read = GuideRead.Complete, freshness = EpgFreshness.Unknown)

        assertThat(guideAgeOf(status)).isNull()
    }

    // ---- one emission of the day's read, mapped once ---------------------

    @Test
    fun `a cache emission is not yet a complete read`() {
        val status = GuideStatus().afterRead(
            origin = DataOrigin.Cache,
            staleReason = null,
            freshness = null,
            lastImportAt = null,
        )

        assertThat(status.read).isEqualTo(GuideRead.FromCache)
    }

    @Test
    fun `a network emission completes the read`() {
        val status = GuideStatus().afterRead(
            origin = DataOrigin.Network,
            staleReason = null,
            freshness = EpgFreshness.Fresh(Duration.ofMinutes(1)),
            lastImportAt = Instant.parse("2026-09-26T08:00:00Z"),
        )

        assertThat(status.read).isEqualTo(GuideRead.Complete)
        assertThat(status.freshness).isEqualTo(EpgFreshness.Fresh(Duration.ofMinutes(1)))
    }

    @Test
    fun `a cache emission with a reason is a failed read`() {
        val status = GuideStatus().afterRead(
            origin = DataOrigin.Cache,
            staleReason = offline,
            freshness = null,
            lastImportAt = null,
        )

        assertThat(status.read).isEqualTo(GuideRead.Failed(offline))
    }

    private fun programme(id: String): EpgProgramme = EpgProgramme(
        id = id,
        startsAt = Instant.parse("2026-09-24T20:00:00Z"),
        endsAt = Instant.parse("2026-09-24T21:00:00Z"),
        title = "Programme $id",
        description = null,
        category = null,
    )
}
