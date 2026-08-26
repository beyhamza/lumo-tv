"use server";

import { api } from "@/lib/api/client";
import { getSession } from "@/lib/session/session";

/**
 * Records that a channel was actually watched.
 *
 * <p>Sent when playback starts, never when a channel is focused or hovered. The
 * contract is explicit about it, and the reason is not tidiness: a "recently
 * watched" rail built from what a cursor passed over on its way somewhere is
 * noise, and it is the user's own history being made worse.
 *
 * <p>Deliberately silent on failure. This is a side effect of watching
 * television, not a step of it — a rail that misses an entry is a smaller
 * problem than a player that interrupts itself to report one.
 */
export async function recordWatched(channelId: string): Promise<void> {
  const session = await getSession();
  if (!session) return;

  try {
    await api(session.accessToken).PUT("/me/recent-channels", {
      body: { channel_id: channelId },
    });
  } catch {
    // See above: nothing to tell the user, and nothing for them to do.
  }
}
