package tv.lumo.android.core.cast

import com.google.android.gms.cast.CastStatusCodes
import com.google.android.gms.cast.MediaError
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import tv.lumo.android.core.player.cast.CastSignals

/**
 * core:player classifies the receiver's answers without the SDK on its classpath,
 * so it writes the codes out. This holds them against the SDK's own constants.
 */
class CastSdkCodesTest {

    @Test
    fun `the status codes core-player ignores are the SDK's`() {
        assertThat(CastSignals.SUCCESS).isEqualTo(CastStatusCodes.SUCCESS)
        assertThat(CastSignals.INTERRUPTED).isEqualTo(CastStatusCodes.INTERRUPTED)
        assertThat(CastSignals.CANCELED).isEqualTo(CastStatusCodes.CANCELED)
        assertThat(CastSignals.REPLACED).isEqualTo(CastStatusCodes.REPLACED)
    }

    @Test
    fun `904 is LOAD_INTERRUPTED in the SDK too`() {
        assertThat(CastSignals.LOAD_INTERRUPTED).isEqualTo(MediaError.DetailedErrorCode.LOAD_INTERRUPTED)
    }
}
