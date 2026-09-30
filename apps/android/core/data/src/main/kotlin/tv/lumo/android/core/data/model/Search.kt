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
 */
data class SearchPage<T>(
    val items: List<T>,
    val totalElements: Long,
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
