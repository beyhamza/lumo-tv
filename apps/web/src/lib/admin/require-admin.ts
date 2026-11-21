import "server-only";

import { notFound } from "next/navigation";
import { cache } from "react";
import { api } from "@/lib/api/client";
import { fetched } from "@/lib/api/fetched";
import { requireSession } from "@/lib/session/session";

/**
 * The session of an administrator, or a 404 (S16B-08, ADR 0015).
 *
 * A 404 and not a 403: an ordinary account has no reason to learn that these
 * pages exist. This guard only decides what to draw. What protects the data is
 * the API, which refuses `/admin` to anyone else whatever this site does.
 *
 * An API that did not answer throws, and `app/error.tsx` says so: "this page does
 * not exist" would be false for an administrator during an outage.
 */
export const requireAdmin = cache(async () => {
  const session = await requireSession();
  const me = await fetched(() => api(session.accessToken).GET("/me", {}));
  if (me.state !== "ok") throw new Error("The account could not be read");
  if (!me.data.is_admin) notFound();
  return session;
});
