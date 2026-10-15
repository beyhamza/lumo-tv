package tv.lumo.android.core.data

import com.google.common.truth.Truth.assertThat
import java.io.IOException
import org.junit.Test
import tv.lumo.android.core.data.model.FavoriteMembership
import tv.lumo.android.core.data.model.FavoriteRemovalOutcome
import tv.lumo.android.network.generated.model.ErrorCode

/**
 * "Remove from all my favourites": the plan, the retry's scope and the verdict
 * (S11-00, G2; FO-05 → FO-08, FO-12).
 */
class GlobalFavoriteRemovalTest {

    private val inDocs = FavoriteMembership("f1", "docs")
    private val inNews = FavoriteMembership("f2", "news")
    private val inKids = FavoriteMembership("f3", "kids")
    private val all = listOf(inDocs, inNews, inKids)

    private val refused = LumoError.Api(ErrorCode.INTERNAL_ERROR, detail = null)
    private val lost = LumoError.Offline(IOException("lost"))

    @Test
    fun `the first attempt targets every membership the server lists`() {
        assertThat(GlobalFavoriteRemoval.targets(all, restrictTo = null)).isEqualTo(all)
    }

    @Test
    fun `FO-06 and FO-12 - a retry targets what remains of the first attempt, never a newer membership`() {
        val addedMeanwhile = FavoriteMembership("f9", "cinema")

        val targets = GlobalFavoriteRemoval.targets(listOf(inKids, addedMeanwhile), restrictTo = setOf("f3"))

        assertThat(targets).containsExactly(inKids)
    }

    @Test
    fun `FAVORITE_NOT_FOUND means already gone, other refusals do not`() {
        assertThat(GlobalFavoriteRemoval.isAlreadyGone(LumoError.Api(ErrorCode.FAVORITE_NOT_FOUND, null))).isTrue()
        assertThat(GlobalFavoriteRemoval.isAlreadyGone(refused)).isFalse()
        assertThat(GlobalFavoriteRemoval.isAlreadyGone(lost)).isFalse()
    }

    @Test
    fun `everything gone after the re-read is a removal`() {
        val outcome = GlobalFavoriteRemoval.outcome(all, failures = emptyMap(), presentAfter = setOf("other"))

        assertThat(outcome).isEqualTo(FavoriteRemovalOutcome.Removed)
    }

    @Test
    fun `FO-05 - two removals out of three succeed - the remaining one is named, the two are kept`() {
        val outcome = GlobalFavoriteRemoval.outcome(all, failures = mapOf("f3" to refused), presentAfter = setOf("f3"))

        assertThat(outcome).isEqualTo(FavoriteRemovalOutcome.Incomplete(listOf(inKids), refused))
    }

    @Test
    fun `FO-08 - a lost answer whose removal did happen is a removal once re-read`() {
        val outcome = GlobalFavoriteRemoval.outcome(all, failures = mapOf("f2" to lost), presentAfter = emptySet())

        assertThat(outcome).isEqualTo(FavoriteRemovalOutcome.Removed)
    }

    @Test
    fun `FO-08 - without a re-read, a lost answer stays unconfirmed - neither removed nor remaining`() {
        val outcome = GlobalFavoriteRemoval.outcome(all, failures = mapOf("f2" to lost), presentAfter = null)

        assertThat(outcome).isEqualTo(FavoriteRemovalOutcome.Unconfirmed(listOf(inNews), lost))
    }

    @Test
    fun `without a re-read, removals that all answered are known`() {
        val outcome = GlobalFavoriteRemoval.outcome(all, failures = emptyMap(), presentAfter = null)

        assertThat(outcome).isEqualTo(FavoriteRemovalOutcome.Removed)
    }
}
