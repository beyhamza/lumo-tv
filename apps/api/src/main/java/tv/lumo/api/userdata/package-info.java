/**
 * Favourites, favourite groups, playback progress and recently watched channels.
 *
 * <p>Implemented. {@link tv.lumo.api.userdata.UserdataController} serves all nine
 * operations of the {@code userdata} tag; the tables have existed since
 * {@code 0007-userdata.sql}, and {@code recent_channel} was added by
 * {@code 0013-recent-channels.sql} when the mobile and television canvases showed
 * a rail that {@code playback_progress} cannot feed.
 *
 * <p><b>Two tables, on purpose.</b> A playback position means nothing on a
 * continuous stream: folding live channels into {@code playback_progress} would
 * make {@code position_ms} a required column with no value to put in it. They
 * render in the same rail; that is not a reason to share storage.
 *
 * <p><b>Recently watched is a rolling window, not a history.</b> The newest fifty
 * entries per account, pruned on write. Nobody asked to page through last month's
 * viewing, and keeping it would be retaining a record of what someone watched on
 * their own television for no feature at all.
 */
package tv.lumo.api.userdata;
