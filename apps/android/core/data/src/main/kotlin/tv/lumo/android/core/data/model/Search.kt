package tv.lumo.android.core.data.model

import tv.lumo.android.core.data.LumoResult

/**
 * What a search is looking through (US-021, S10-01).
 *
 * The product's four filters, and the reason this is one type rather than three
 * booleans: a search has exactly one of them at a time, and the screen, the
 * request and the state all have to agree on which. [All] is the grouped view:
 * up to four hits of each type, on one screen. The other three are the list of
 * one type, twenty at a time.
 */
enum class SearchFilter {
    All,
    Channels,
    Films,
    Series;

    /** Whether [All] — and [other] — includes this filter's type. */
    fun includes(other: SearchFilter): Boolean =
        this == All || other == All || this == other
}

/**
 * Which of a source's three catalogues actually hold something (US-021, S10-02).
 *
 * <h2>Why the filters are not always all four</h2>
 *
 * A playlist can carry channels and no film, and an M3U source never carries
 * series (`adr/0010`). Offering a filter that opens onto nothing is a promise
 * nobody can keep, so the search reads the presence once per source and the screen
 * lists only the types that are there (SR-11, S10-E01).
 *
 * <h2>Fail-open, and it is the safe direction to be wrong in</h2>
 *
 * The default is every type present, and a probe that did not answer leaves its
 * type present. Hiding "Films" on an outage would repeat a mistake this project
 * already paid for: a missing tab is read as a missing feature, and an empty list
 * at least explains itself. A source with no films answers `0` rather than an
 * error, so its filter is genuinely absent.
 */
data class CataloguePresence(
    val channels: Boolean = true,
    val films: Boolean = true,
    val series: Boolean = true,
) {

    /**
     * Whether this source carries the type [filter] stands for.
     *
     * [SearchFilter.All] is carried as soon as any of the three catalogues is:
     * the grouped view is the default, and a source with one catalogue still has
     * something to search.
     */
    fun carries(filter: SearchFilter): Boolean = when (filter) {
        SearchFilter.All -> channels || films || series
        SearchFilter.Channels -> channels
        SearchFilter.Films -> films
        SearchFilter.Series -> series
    }

    companion object {
        /** Every type present: the fail-open value, used while the probe is unknown. */
        val all = CataloguePresence()
    }
}

/**
 * One page of search hits, and the total the page was cut from.
 *
 * A page carries what the endpoint answered and nothing else. In particular it
 * does **not** carry a "next page" index: Q9 is explicit that the preview of four
 * and the pages of twenty are distinct requests, so "Voir tous" starts a page 0 of
 * twenty rather than reusing an index computed for a size of four. Keeping a page
 * number here would invite exactly that arithmetic.
 *
 * @param totalElements total hits of this type for the query, not the size of
 *   [items]. It is what tells "Voir tous" whether it is worth offering, and it is
 *   read straight from the contract's `total_elements`.
 * @param fromCache true when this page came from the device's cache because the
 *   server could not be reached (S10-04). The items are what was last
 *   synchronised, not the server's current answer, and a screen says so rather
 *   than presenting them as fresh (Q9, "données potentiellement anciennes").
 */
data class SearchPage<T>(
    val items: List<T>,
    val totalElements: Long,
    val fromCache: Boolean = false,
)

/**
 * What one search answered, one result per requested type.
 *
 * <h2>Why the types are independent fields and not a `Map`</h2>
 *
 * The three hits have three different types — [Channel], [VodItem], [Series] —
 * and a map would erase them into an `Any`, which is the sort of thing a screen
 * then casts. Three fields keep the type of each section, and a reader sees at a
 * glance which sections a filter asks for.
 *
 * <h2>Why null is not a failure</h2>
 *
 * A filter **chooses** its sections: [SearchFilter.All] asks for all three, and
 * [SearchFilter.Films] asks for films alone. A field that stays null is a section
 * this search did not ask for, and it is deliberately distinct from
 * `LumoResult.Failure`, which is a section that was asked for and could not be
 * fetched. A screen must be able to tell "not requested" from "went wrong" — the
 * first is a filter, the second is an error to report and retry (Q9, SR-10).
 *
 * The independence is the point: assembling this never fails as a whole. One
 * section's `Failure` leaves the other two `Success`, so a search answers with two
 * working sections rather than none (Q9, "une erreur n'est jamais un ensemble
 * vide").
 */
data class SearchResults(
    val channels: LumoResult<SearchPage<Channel>>? = null,
    val films: LumoResult<SearchPage<VodItem>>? = null,
    val series: LumoResult<SearchPage<Series>>? = null,
)
