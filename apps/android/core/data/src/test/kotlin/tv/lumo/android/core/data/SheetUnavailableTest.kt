package tv.lumo.android.core.data

import com.google.common.truth.Truth.assertThat
import java.io.IOException
import org.junit.Test
import tv.lumo.android.network.generated.model.ErrorCode

/**
 * BUG-S11-06-04: a sheet opened offline with nothing cached stayed an empty
 * skeleton. Which explanation a sheet gives, and when it gives none.
 */
class SheetUnavailableTest {

    private val offline = LumoResult.Failure(LumoError.Offline(IOException("lost")))
    private val refused = LumoResult.Failure(LumoError.Api(ErrorCode.INTERNAL_ERROR, detail = null))

    @Test
    fun `nothing cached and no network says offline`() {
        assertThat(SheetUnavailable.of(hasContent = false, lastLoad = offline))
            .isEqualTo(SheetUnavailable.Offline)
    }

    @Test
    fun `nothing cached and a refusal says it could not be loaded`() {
        assertThat(SheetUnavailable.of(hasContent = false, lastLoad = refused))
            .isEqualTo(SheetUnavailable.Failed)
    }

    @Test
    fun `a cached row is a sheet, whatever the fetch did`() {
        assertThat(SheetUnavailable.of(hasContent = true, lastLoad = offline)).isNull()
    }

    @Test
    fun `a fetch still in flight, or one that worked, is not a failure`() {
        assertThat(SheetUnavailable.of(hasContent = false, lastLoad = null)).isNull()
        assertThat(SheetUnavailable.of(hasContent = false, lastLoad = LumoResult.Success(Unit))).isNull()
    }
}
