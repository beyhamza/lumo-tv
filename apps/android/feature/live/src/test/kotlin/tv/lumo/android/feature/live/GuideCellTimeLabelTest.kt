package tv.lumo.android.feature.live

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The guide cell's time line shows the programme **start only** (#215, S9-05).
 *
 * The whole range used to be printed, and on a 12-hour clock
 * `12:00 PM – 12:30 PM` did not fit the cell's ~200 dp content box, so the end
 * was clipped. This pins the shape of the label without Compose.
 */
class GuideCellTimeLabelTest {

    @Test
    fun `the cell time is the start alone, without the range separator`() {
        val label = gridCellTimeLabel(startLabel = "12:00 PM", endLabel = "12:30 PM")

        assertThat(label).isEqualTo("12:00 PM")
        assertThat(label).doesNotContain("–")
    }

    @Test
    fun `the end label never reaches the cell`() {
        assertThat(gridCellTimeLabel(startLabel = "12:00 PM", endLabel = "12:30 PM"))
            .doesNotContain("12:30")
    }
}
