package tv.lumo.android.feature.live

import com.google.common.truth.Truth.assertThat
import java.time.Instant
import org.junit.Test
import tv.lumo.android.core.data.CatalogueSource
import tv.lumo.android.core.data.model.EpgProgramme

/**
 * The programme sheet's moments and the two acceptances it owns (S9-06-01,
 * GD-07/08).
 *
 * <h2>What is pinned here, and why not on a device</h2>
 *
 * GD-07 ("the action disappears at the end, the focus joins Fermer, the title
 * does not change") and GD-08 ("a future programme that starts gains the action
 * without taking the focus, the state is read again on activation") are rules of
 * instants, not of a remote. Left to a real television they would be found by
 * waiting until a programme ends, which is a test nobody runs twice. As pure
 * functions they are ordinary unit tests, and the Compose panels only draw what
 * they return.
 *
 * <h2>Half-open, as everywhere else in the guide</h2>
 *
 * A programme is current from its start inclusive to its end exclusive: at 21:00
 * exactly a `20:00–21:00` programme is over and the `21:00–` one is current. The
 * same rule the grid selection follows, so the sheet and the grid can never
 * disagree about which programme is on.
 */
class ProgrammeSheetTest {

    private val programme = EpgProgramme(
        id = "p1",
        startsAt = Instant.parse("2026-09-24T20:00:00Z"),
        endsAt = Instant.parse("2026-09-24T21:00:00Z"),
        title = "Le journal",
        description = "Le résumé du jour",
        category = "Info",
    )

    private fun at(time: String) = Instant.parse(time)

    // ---- the three moments -------------------------------------------------

    @Test
    fun `a programme is current from its start inclusive to its end exclusive`() {
        assertThat(momentOf(programme, at("2026-09-24T19:59:59Z")))
            .isEqualTo(ProgrammeMoment.Future)
        assertThat(momentOf(programme, at("2026-09-24T20:00:00Z")))
            .isEqualTo(ProgrammeMoment.Current)
        assertThat(momentOf(programme, at("2026-09-24T20:59:59Z")))
            .isEqualTo(ProgrammeMoment.Current)
        assertThat(momentOf(programme, at("2026-09-24T21:00:00Z")))
            .isEqualTo(ProgrammeMoment.Past)
        assertThat(momentOf(programme, at("2026-09-24T22:00:00Z")))
            .isEqualTo(ProgrammeMoment.Past)
    }

    // ---- GD-08: the action follows the moment ------------------------------

    @Test
    fun `only a current programme offers the watch action`() {
        assertThat(watchAvailable(ProgrammeMoment.Future)).isFalse()
        assertThat(watchAvailable(ProgrammeMoment.Current)).isTrue()
        assertThat(watchAvailable(ProgrammeMoment.Past)).isFalse()
    }

    @Test
    fun `a future programme that starts gains the action without taking the focus`() {
        assertThat(watchAllowed(programme, at("2026-09-24T19:30:00Z"))).isFalse()
        assertThat(watchAllowed(programme, at("2026-09-24T20:00:00Z"))).isTrue()

        // The focus rule leaves a focus that was not on the action where it is:
        // GD-08 says "without stealing the focus", not "give it the action".
        assertThat(sheetFocusAfter(ProgrammeMoment.Current, ProgrammeSheetFocus.Close))
            .isEqualTo(ProgrammeSheetFocus.Close)
    }

    @Test
    fun `the action is read again at the end, so a sheet left open cannot play`() {
        assertThat(watchAllowed(programme, at("2026-09-24T20:10:00Z"))).isTrue()
        assertThat(watchAllowed(programme, at("2026-09-24T21:00:01Z"))).isFalse()
    }

    // ---- GD-07: the action disappears and the focus joins Fermer -----------

    @Test
    fun `the focus stays on the action while the programme is current`() {
        assertThat(sheetFocusAfter(ProgrammeMoment.Current, ProgrammeSheetFocus.Watch))
            .isEqualTo(ProgrammeSheetFocus.Watch)
    }

    @Test
    fun `a focus on the action joins Fermer when the programme ends`() {
        assertThat(sheetFocusAfter(ProgrammeMoment.Past, ProgrammeSheetFocus.Watch))
            .isEqualTo(ProgrammeSheetFocus.Close)
    }

    @Test
    fun `a focus already on Fermer is never moved`() {
        assertThat(sheetFocusAfter(ProgrammeMoment.Past, ProgrammeSheetFocus.Close))
            .isEqualTo(ProgrammeSheetFocus.Close)
        assertThat(sheetFocusAfter(ProgrammeMoment.Future, ProgrammeSheetFocus.Close))
            .isEqualTo(ProgrammeSheetFocus.Close)
    }

    // ---- the sheet belongs to the source -----------------------------------

    @Test
    fun `changing source closes the sheet`() {
        val sheet = ProgrammeSheet(
            channelId = "c1",
            channelName = "Chaîne 1",
            programme = programme,
        )
        val open = LiveState(sourceId = "source-a", programmeSheet = sheet)

        val next = open.browsing(CatalogueSource.Ready("source-b", isPlaylist = true))

        assertThat(next.sourceId).isEqualTo("source-b")
        assertThat(next.programmeSheet).isNull()
    }

    @Test
    fun `a mere status change of the same source keeps the sheet`() {
        val sheet = ProgrammeSheet(
            channelId = "c1",
            channelName = "Chaîne 1",
            programme = programme,
        )
        val open = LiveState(sourceId = "source-a", programmeSheet = sheet)

        val next = open.browsing(
            CatalogueSource.FirstImport("source-a", isPlaylist = true, failed = false),
        )

        assertThat(next.programmeSheet).isEqualTo(sheet)
    }
}
