package tv.lumo.android.core.data

import androidx.paging.PagingSource
import com.google.common.truth.Truth.assertThat
import java.util.UUID
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import tv.lumo.android.core.data.internal.ApiCaller
import tv.lumo.android.core.data.internal.ChannelResolver
import tv.lumo.android.core.data.internal.ProblemReader
import tv.lumo.android.core.data.model.FavoriteRemovalOutcome
import tv.lumo.android.core.data.model.FavoriteReorderOutcome
import tv.lumo.android.core.data.repository.FavoriteRepository
import tv.lumo.android.core.database.dao.ChannelDao
import tv.lumo.android.core.database.dao.FavoriteDao
import tv.lumo.android.core.database.model.ChannelEntity
import tv.lumo.android.core.database.model.FavoriteChannelRow
import tv.lumo.android.core.database.model.FavoriteEntity
import tv.lumo.android.core.database.model.FavoriteGroupEntity
import tv.lumo.android.core.database.model.UnresolvedFavorite
import tv.lumo.android.network.generated.api.CatalogApi
import tv.lumo.android.network.generated.api.UserdataApi
import tv.lumo.android.network.generated.infrastructure.Serializer

/**
 * Filling the favourites cache, and resolving what it points at (US-12).
 *
 * <h2>Why this is worth a test</h2>
 *
 * Three behaviours here are invisible until somebody's phone is wrong.
 *
 * The first is the chunking. `GET /sources/{id}/channels?ids=` is capped at 100 by
 * the contract, and a group of three hundred favourites is three calls. A caller
 * that sends all three hundred gets a `400`; a caller that sends exactly a hundred
 * and forgets `size` gets fifty rows, a `200`, and no indication that half of them
 * were dropped. The second failure is the dangerous one, because it looks like a
 * catalogue that simply has fewer channels.
 *
 * The second is that failing to *name* a favourite is not failing to load
 * favourites. The two lists are already written when the resolution runs; a source
 * this device has never synchronised must not turn somebody's perfectly good
 * favourites into an error screen.
 *
 * The third is what a write does to the cache afterwards. Adds and renames touch
 * one row; moves and group deletions make the server renumber rows this device
 * holds, and guessing what the renumbering did is how a local order quietly stops
 * matching the account's.
 *
 * The DAOs are fakes rather than a real database, as in [CatalogueRepositoryTest]:
 * what is under test is the walk and the bookkeeping, and Room needs an Android
 * runtime the unit build deliberately does not ask for (AGENTS.md §8). The join
 * that hides an orphan favourite is Room's own and is exercised on a device, not
 * here. Since S11-05 that join is a left join: an unresolved favourite is a row
 * with a null channel, and its mapping is held here.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FavoriteRepositoryTest {

    private lateinit var server: MockWebServer
    private lateinit var userdata: UserdataApi
    private lateinit var catalog: CatalogApi

    private val favoriteDao = FakeFavoriteDao()
    private val channelDao = FakeChannelDaoForFavorites()

    private val sourceId = UUID.randomUUID().toString()
    private val groupId = UUID.randomUUID().toString()

    @Before
    fun start() {
        server = MockWebServer()
        server.start()
        val retrofit = Retrofit.Builder()
            .baseUrl(server.url("/").toString())
            .addConverterFactory(MoshiConverterFactory.create(Serializer.moshiBuilder.build()))
            .build()
        userdata = retrofit.create(UserdataApi::class.java)
        catalog = retrofit.create(CatalogApi::class.java)
    }

    @After
    fun stop() = server.shutdown()

    @Test
    fun `unresolved favourites are fetched in chunks of a hundred, with an explicit size`() =
        runTest {
            val channelIds = List(250) { UUID.randomUUID().toString() }
            favoriteDao.unresolved += channelIds.map { UnresolvedFavorite(sourceId, it) }

            server.enqueue(json(groupList()))
            server.enqueue(json(favoriteList()))
            server.enqueue(json(channelsById(channelIds.take(100))))
            server.enqueue(json(channelsById(channelIds.drop(100).take(100))))
            server.enqueue(json(channelsById(channelIds.drop(200))))

            repository().refresh()

            server.takeRequest() // groups
            server.takeRequest() // favourites
            val calls = listOf(server.takeRequest(), server.takeRequest(), server.takeRequest())

            // Three calls, not one of 250 — which the contract answers with a 400.
            assertThat(calls.map { it.idCount() }).containsExactly(100, 100, 50).inOrder()
            // And `size` on every one of them. Without it the page defaults to 50
            // and the answer is half a chunk, with a 200 and no error.
            assertThat(calls.map { it.query("size") }).containsExactly("100", "100", "100")
            assertThat(channelDao.stored).hasSize(250)
        }

    @Test
    fun `a source that cannot be reached does not fail a refresh that already worked`() = runTest {
        favoriteDao.unresolved += UnresolvedFavorite(sourceId, UUID.randomUUID().toString())

        server.enqueue(json(groupList(name = "Documentaire")))
        server.enqueue(json(favoriteList(count = 1)))
        server.enqueue(MockResponse().setResponseCode(503).setBody("""{"code":"INTERNAL_ERROR"}"""))

        val result = repository().refresh()

        // The favourites are in the cache. What is missing is the readable name of
        // one of them, which the next refresh resolves. An error here would be an
        // error screen over somebody's perfectly good favourites — the partial
        // state is carried by the rows instead (`resolved`, S11-05).
        assertThat(result).isEqualTo(LumoResult.Success(Unit))
        assertThat(favoriteDao.groups.value.map { it.name }).containsExactly("Documentaire")
        assertThat(favoriteDao.favorites).hasSize(1)
    }

    @Test
    fun `a favourite whose channel is not resolved stays listed, marked so (S11-05)`() = runTest {
        val channelId = UUID.randomUUID().toString()
        favoriteDao.rows.value = listOf(
            FavoriteChannelRow(
                favoriteId = "f-1",
                groupId = groupId,
                favoritePosition = 0,
                favoriteSourceId = sourceId,
                favoriteChannelId = channelId,
                channel = null,
            ),
            FavoriteChannelRow(
                favoriteId = "f-2",
                groupId = groupId,
                favoritePosition = 1,
                favoriteSourceId = sourceId,
                favoriteChannelId = "c-2",
                channel = channelEntity("c-2", name = "Chaîne 2"),
            ),
        )

        val listed = repository().favorites().first()

        // Never dropped: an outage during the resolution used to make it vanish.
        assertThat(listed.map { it.favoriteId }).containsExactly("f-1", "f-2").inOrder()
        val unresolved = listed.first()
        assertThat(unresolved.resolved).isFalse()
        // Its ids survive, so it can be filtered by source and removed.
        assertThat(unresolved.channel.id).isEqualTo(channelId)
        assertThat(unresolved.channel.sourceId).isEqualTo(sourceId)
        assertThat(listed[1].resolved).isTrue()
        assertThat(listed[1].channel.name).isEqualTo("Chaîne 2")
    }

    @Test
    fun `one unreachable source does not cost the names resolved from another`() = runTest {
        val other = UUID.randomUUID().toString()
        val mine = UUID.randomUUID().toString()
        val theirs = UUID.randomUUID().toString()
        val resolver = ChannelResolver(catalog = catalog, calls = calls(), channelDao = channelDao)

        server.enqueue(json(channelsById(listOf(mine))))
        server.enqueue(MockResponse().setResponseCode(503).setBody("""{"code":"INTERNAL_ERROR"}"""))

        val failure = resolver.resolve(listOf(sourceId to mine, other to theirs))

        // The failure is reported, and what did resolve is kept.
        assertThat(failure).isNotNull()
        assertThat(channelDao.stored.map { it.id }).containsExactly(mine)
    }

    @Test
    fun `a refresh replaces both lists whole, so a favourite removed elsewhere disappears`() =
        runTest {
            favoriteDao.favorites += favoriteEntity(UUID.randomUUID().toString())
            favoriteDao.groups.value = listOf(groupEntity("Removed on the phone"))

            server.enqueue(json(groupList(name = "Ciné")))
            server.enqueue(json(favoriteList(count = 0)))

            repository().refresh()

            assertThat(favoriteDao.groups.value.map { it.name }).containsExactly("Ciné")
            // Merging would keep a favourite the account no longer has, forever.
            assertThat(favoriteDao.favorites).isEmpty()
        }

    @Test
    fun `the first add pulls the groups, because the default group was just created`() = runTest {
        server.enqueue(json(favorite(UUID.randomUUID().toString())))
        server.enqueue(json(groupList(name = "Favorites", isDefault = true)))

        val result = repository().add(UUID.randomUUID().toString())

        assertThat(result).isEqualTo(LumoResult.Success(Unit))
        // A favourite filed in a group this device has never heard of renders
        // nowhere. "Created on the first add" is the server's behaviour, and this
        // is what makes it visible.
        assertThat(favoriteDao.groups.value.single().isDefault).isTrue()
    }

    @Test
    fun `an add into a known group does not re-read the groups`() = runTest {
        favoriteDao.groups.value = listOf(groupEntity("Documentaire"))
        server.enqueue(json(favorite(UUID.randomUUID().toString())))

        repository().add(UUID.randomUUID().toString(), groupId)

        // One request. Starring is the frequent gesture of this feature and it does
        // not get to cost three round trips.
        assertThat(server.requestCount).isEqualTo(1)
    }

    @Test
    fun `moving a favourite re-reads the list rather than guessing the renumbering`() = runTest {
        val favoriteId = UUID.randomUUID().toString()
        favoriteDao.favorites += favoriteEntity(favoriteId)

        server.enqueue(json(favorite(favoriteId)))
        server.enqueue(json(favoriteList(count = 2)))

        val result = repository().move(favoriteId, position = 0)

        assertThat(result).isEqualTo(LumoResult.Success(Unit))
        // The server renumbers every favourite the move displaced. Patching the one
        // row that came back would leave the others at positions the account no
        // longer has.
        assertThat(favoriteDao.favorites).hasSize(2)
    }

    @Test
    fun `a write that fails leaves the cache untouched`() = runTest {
        val favoriteId = UUID.randomUUID().toString()
        favoriteDao.favorites += favoriteEntity(favoriteId)

        server.enqueue(MockResponse().setResponseCode(503).setBody("""{"code":"INTERNAL_ERROR"}"""))

        val result = repository().remove(favoriteId)

        // Nothing is queued for later: US-12 asks for an offline modification to be
        // refused explicitly rather than silently lost, and a cache that dropped the
        // row would be exactly that silent loss.
        assertThat(result).isInstanceOf(LumoResult.Failure::class.java)
        assertThat(favoriteDao.favorites.map { it.id }).containsExactly(favoriteId)
    }

    @Test
    fun `BUG-S11-06-01 - deleting a group that answers 204 is a success, and both lists are re-read`() =
        runTest {
            // `DELETE /me/favorite-groups/{id}` answers 204 No Content. Reading that
            // as "a body was expected" announced a failure the server never had.
            server.enqueue(MockResponse().setResponseCode(204))
            server.enqueue(json(groupList(name = "Favorites", isDefault = true)))
            server.enqueue(json(favoriteList(count = 2)))

            val result = repository().deleteGroup(groupId)

            assertThat(result).isEqualTo(LumoResult.Success(Unit))
            assertThat(server.takeRequest().method).isEqualTo("DELETE")
            assertThat(favoriteDao.groups.value.map { it.name }).containsExactly("Favorites")
            assertThat(favoriteDao.favorites).hasSize(2)
        }

    // ---- S11-03: visible reorder and global removal --------------------------

    @Test
    fun `FO-02 - a visible reorder reads the group, sends full-group moves and re-reads`() = runTest {
        // A* in the active source, B* in another; the full group is interleaved.
        val ids = listOf("A1", "B1", "A2", "B2", "A3").associateWith { UUID.randomUUID().toString() }
        server.enqueue(json(groupOrder(ids.values.toList())))
        repeat(3) { server.enqueue(json(favorite(UUID.randomUUID().toString()))) }
        server.enqueue(json(favoriteList(count = 5)))

        val outcome = repository().reorderVisible(groupId, listOf("A3", "A1", "A2").map(ids::getValue))

        assertThat(outcome).isEqualTo(FavoriteReorderOutcome.Applied)
        val read = server.takeRequest()
        assertThat(read.method).isEqualTo("GET")
        assertThat(read.requestUrl!!.queryParameter("groupId")).isEqualTo(groupId)
        val moves = List(3) { server.takeRequest() }
        // A3 to the head, then B1 and B2 back to the places they had: 1 and 3.
        assertThat(moves.map { it.method }).containsExactly("PATCH", "PATCH", "PATCH")
        assertThat(moves.map { it.path!!.substringAfterLast('/') })
            .containsExactly(ids["A3"], ids["B1"], ids["B2"]).inOrder()
        assertThat(moves.map { it.body.readUtf8() })
            .containsExactly("""{"position":0}""", """{"position":1}""", """{"position":3}""").inOrder()
        assertThat(server.takeRequest().method).isEqualTo("GET")
        assertThat(favoriteDao.favorites).hasSize(5)
    }

    @Test
    fun `a reorder that fails midway keeps the accepted moves, says so, and re-reads`() = runTest {
        val ids = listOf("A1", "B1", "A2", "B2", "A3").associateWith { UUID.randomUUID().toString() }
        server.enqueue(json(groupOrder(ids.values.toList())))
        server.enqueue(json(favorite(UUID.randomUUID().toString())))
        server.enqueue(MockResponse().setResponseCode(503).setBody("""{"code":"INTERNAL_ERROR"}"""))
        server.enqueue(json(favoriteList(count = 5)))

        val outcome = repository().reorderVisible(groupId, listOf("A3", "A1", "A2").map(ids::getValue))

        // Never a success, and no third move after the failure.
        assertThat(outcome).isInstanceOf(FavoriteReorderOutcome.NotFullyApplied::class.java)
        outcome as FavoriteReorderOutcome.NotFullyApplied
        assertThat(outcome.applied).isEqualTo(1)
        assertThat(outcome.planned).isEqualTo(3)
        assertThat(server.requestCount).isEqualTo(4)
        // The cache is the server's re-read, not a guess at the half-done order.
        assertThat(favoriteDao.favorites).hasSize(5)
    }

    @Test
    fun `a reorder whose group cannot be read sends nothing`() = runTest {
        server.enqueue(MockResponse().setResponseCode(503).setBody("""{"code":"INTERNAL_ERROR"}"""))

        val outcome = repository().reorderVisible(groupId, listOf(UUID.randomUUID().toString()))

        assertThat(outcome).isInstanceOf(FavoriteReorderOutcome.NotApplied::class.java)
        assertThat(server.requestCount).isEqualTo(1)
    }

    @Test
    fun `FO-05 - removing everywhere keeps the successes and names what remains`() = runTest {
        val channelId = UUID.randomUUID().toString()
        val (f1, f2, f3) = List(3) { UUID.randomUUID().toString() }
        val other = UUID.randomUUID().toString()
        server.enqueue(json(memberships(channelId, listOf(f1, f2, f3), other)))
        server.enqueue(MockResponse().setResponseCode(204))
        // Already removed elsewhere: a success, not a failure.
        server.enqueue(MockResponse().setResponseCode(404).setBody("""{"code":"FAVORITE_NOT_FOUND"}"""))
        server.enqueue(MockResponse().setResponseCode(503).setBody("""{"code":"INTERNAL_ERROR"}"""))
        server.enqueue(json(memberships(channelId, listOf(f3), other)))

        val outcome = repository().removeEverywhere(channelId)

        assertThat(outcome).isInstanceOf(FavoriteRemovalOutcome.Incomplete::class.java)
        assertThat((outcome as FavoriteRemovalOutcome.Incomplete).remaining.map { it.favoriteId })
            .containsExactly(f3)
        server.takeRequest()
        val deletes = List(3) { server.takeRequest() }
        assertThat(deletes.map { it.method }).containsExactly("DELETE", "DELETE", "DELETE")
        // The other channel's favourite was never touched.
        assertThat(deletes.map { it.path!!.substringAfterLast('/') }).containsExactly(f1, f2, f3).inOrder()
        assertThat(favoriteDao.favorites.map { it.id }).containsExactly(f3, other)
    }

    @Test
    fun `FO-06 - the retry targets only the remaining membership, re-read first`() = runTest {
        val channelId = UUID.randomUUID().toString()
        val f3 = UUID.randomUUID().toString()
        val addedMeanwhile = UUID.randomUUID().toString()
        server.enqueue(json(memberships(channelId, listOf(f3, addedMeanwhile))))
        server.enqueue(MockResponse().setResponseCode(204))
        server.enqueue(json(memberships(channelId, listOf(addedMeanwhile))))

        val outcome = repository().removeEverywhere(channelId, restrictTo = setOf(f3))

        assertThat(outcome).isEqualTo(FavoriteRemovalOutcome.Removed)
        assertThat(server.takeRequest().method).isEqualTo("GET")
        assertThat(server.takeRequest().path!!.substringAfterLast('/')).isEqualTo(f3)
        assertThat(server.takeRequest().method).isEqualTo("GET")
        assertThat(server.requestCount).isEqualTo(3)
    }

    @Test
    fun `FO-08 - a lost answer with no re-read is unconfirmed, not removed`() = runTest {
        val channelId = UUID.randomUUID().toString()
        val f1 = UUID.randomUUID().toString()
        server.enqueue(json(memberships(channelId, listOf(f1))))
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST))
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST))

        val outcome = repository().removeEverywhere(channelId)

        assertThat(outcome).isInstanceOf(FavoriteRemovalOutcome.Unconfirmed::class.java)
        assertThat((outcome as FavoriteRemovalOutcome.Unconfirmed).unconfirmed.map { it.favoriteId })
            .containsExactly(f1)
    }

    @Test
    fun `removing one membership that is already gone is a success`() = runTest {
        val favoriteId = UUID.randomUUID().toString()
        favoriteDao.favorites += favoriteEntity(favoriteId)
        server.enqueue(MockResponse().setResponseCode(404).setBody("""{"code":"FAVORITE_NOT_FOUND"}"""))
        server.enqueue(json(favoriteList(count = 0)))

        val result = repository().remove(favoriteId)

        assertThat(result).isEqualTo(LumoResult.Success(Unit))
        assertThat(favoriteDao.favorites).isEmpty()
    }

    // ---- helpers -----------------------------------------------------------

    private fun groupOrder(ids: List<String>): String {
        val items = ids.mapIndexed { position, id ->
            """{"id":"$id","group_id":"$groupId","source_id":"$sourceId","channel_id":"${UUID.randomUUID()}","position":$position}"""
        }
        return """{"items":[${items.joinToString(",")}]}"""
    }

    private fun memberships(channelId: String, ids: List<String>, otherChannelFavorite: String? = null): String {
        val mine = ids.mapIndexed { position, id ->
            """{"id":"$id","group_id":"${UUID.randomUUID()}","source_id":"$sourceId","channel_id":"$channelId","position":$position}"""
        }
        val other = listOfNotNull(otherChannelFavorite).map { id ->
            """{"id":"$id","group_id":"$groupId","source_id":"$sourceId","channel_id":"${UUID.randomUUID()}","position":0}"""
        }
        return """{"items":[${(mine + other).joinToString(",")}]}"""
    }

    private fun repository() = FavoriteRepository(
        api = userdata,
        calls = calls(),
        favoriteDao = favoriteDao,
        resolver = ChannelResolver(
            catalog = catalog,
            calls = calls(),
            channelDao = channelDao,
        ),
        io = UnconfinedTestDispatcher(),
    )

    private fun calls() = ApiCaller(ProblemReader(Serializer.moshiBuilder.build()))

    private fun json(body: String) = MockResponse().setResponseCode(200).setBody(body)

    private fun groupList(name: String = "Favorites", isDefault: Boolean = false) = """
        {"items":[{"id":"$groupId","name":"$name","position":0,"is_default":$isDefault}]}
    """.trimIndent()

    private fun favoriteList(count: Int = 1): String {
        val items = (0 until count).joinToString(",") { favorite(UUID.randomUUID().toString()) }
        return """{"items":[$items]}"""
    }

    private fun favorite(id: String) = """
        {"id":"$id","group_id":"$groupId","source_id":"$sourceId",
         "channel_id":"${UUID.randomUUID()}","position":0}
    """.trimIndent()

    private fun channelsById(ids: List<String>): String {
        val items = ids.joinToString(",") { id ->
            """{"id":"$id","source_id":"$sourceId","name":"Chaîne","position":0,"is_adult":false}"""
        }
        return """
            {"items":[$items],"page":0,"size":100,
             "total_elements":${ids.size},"total_pages":1}
        """.trimIndent()
    }

    private fun favoriteEntity(id: String) = FavoriteEntity(
        id = id,
        groupId = groupId,
        sourceId = sourceId,
        channelId = UUID.randomUUID().toString(),
        position = 0,
    )

    private fun channelEntity(id: String, name: String) = ChannelEntity(
        id = id,
        sourceId = sourceId,
        categoryId = null,
        externalId = null,
        name = name,
        logoUrl = null,
        tvgId = null,
        number = null,
        quality = null,
        position = 0,
        isAdult = false,
    )

    private fun groupEntity(name: String) = FavoriteGroupEntity(
        id = groupId,
        name = name,
        position = 0,
        isDefault = false,
    )

    private fun RecordedRequest.idCount(): Int =
        requestUrl!!.queryParameterValues("ids").size

    private fun RecordedRequest.query(name: String): String? =
        requestUrl!!.queryParameter(name)
}

/**
 * The two favourite tables, in memory.
 *
 * `replaceGroups` and `replaceFavorites` are inherited from the interface rather
 * than reimplemented: their delete-then-insert is part of what these tests check,
 * and a fake that reimplemented them could pass while the real one was wrong.
 */
private class FakeFavoriteDao : FavoriteDao {

    val groups = MutableStateFlow<List<FavoriteGroupEntity>>(emptyList())
    val favorites = mutableListOf<FavoriteEntity>()
    val unresolved = mutableListOf<UnresolvedFavorite>()
    val rows = MutableStateFlow<List<FavoriteChannelRow>>(emptyList())

    override fun observeGroups(): Flow<List<FavoriteGroupEntity>> = groups

    override suspend fun countGroup(groupId: String): Int = groups.value.count { it.id == groupId }

    override suspend fun upsertGroups(groups: List<FavoriteGroupEntity>) {
        this.groups.value = this.groups.value + groups
    }

    override suspend fun deleteAllGroups() {
        groups.value = emptyList()
    }

    override fun observeFavorites(groupId: String?): Flow<List<FavoriteChannelRow>> = rows

    override fun observeFavoritedChannelIds(): Flow<List<String>> =
        MutableStateFlow(favorites.map { it.channelId })

    override suspend fun unresolvedFavorites(): List<UnresolvedFavorite> = unresolved

    override suspend fun upsertFavorites(favorites: List<FavoriteEntity>) {
        this.favorites += favorites
    }

    override suspend fun deleteFavorite(favoriteId: String) {
        favorites.removeAll { it.id == favoriteId }
    }

    override suspend fun deleteAllFavorites() {
        favorites.clear()
    }
}

/** Only the two methods the favourites path touches; the rest is the catalogue's. */
private class FakeChannelDaoForFavorites : ChannelDao {

    val stored = mutableListOf<ChannelEntity>()

    override suspend fun upsert(channels: List<ChannelEntity>) {
        stored += channels
    }

    override suspend fun countForSource(sourceId: String): Int =
        stored.count { it.sourceId == sourceId }

    override suspend fun deleteBySource(sourceId: String) = unreachable()

    override fun observe(id: String): Flow<ChannelEntity?> = MutableStateFlow(null)

    override suspend fun byId(id: String): ChannelEntity? = stored.firstOrNull { it.id == id }

    override suspend fun nextAfter(
        sourceId: String,
        position: Int,
        name: String,
    ): ChannelEntity? = unreachable()

    override fun pagedByCategory(
        sourceId: String,
        categoryId: String,
    ): PagingSource<Int, ChannelEntity> = unreachable()

    override fun pagedBySource(sourceId: String): PagingSource<Int, ChannelEntity> = unreachable()

    override fun pagedBySearch(
        sourceId: String,
        query: String,
    ): PagingSource<Int, ChannelEntity> = unreachable()

    override suspend fun searchPage(
        sourceId: String,
        query: String,
        size: Int,
        offset: Int,
    ): List<ChannelEntity> = unreachable()

    override suspend fun searchCount(sourceId: String, query: String): Int = unreachable()

    private fun unreachable(): Nothing =
        throw AssertionError("Not part of the favourites path")
}
