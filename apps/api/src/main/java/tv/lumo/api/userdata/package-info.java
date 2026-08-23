/**
 * Favourites, favourite groups and playback progress.
 *
 * <p><b>Not implemented.</b> {@code GET/POST /me/favorites},
 * {@code DELETE /me/favorites/{id}}, {@code GET/POST /me/favorite-groups} and
 * {@code PUT /me/progress} are defined in the contract but are outside sprint 1,
 * whose vertical slice is sign in, register a source, watch a channel
 * (docs/backlog/sprint-01.md).
 *
 * <p>No controller implements {@code UserdataApi}, so those paths return 404
 * rather than a stub returning invented data. The tables exist
 * ({@code 0007-userdata.sql}) so this can be built without a migration against
 * live data.
 */
package tv.lumo.api.userdata;
