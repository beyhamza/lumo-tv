import "server-only";

import { problemCode } from "@/lib/api/client";
import type { RequestOutcome } from "./page-state";

/**
 * Runs one API call of a catalogue page so that it **cannot throw**.
 *
 * openapi-fetch resolves on any HTTP answer and rejects when there is none — API
 * down, connection reset. Inside a `Promise.all` that rejection takes the whole
 * page to the error boundary, including the parts whose requests had answered.
 * A partly available catalogue keeps its available parts (US-024), so a call
 * with no answer becomes a result with neither `data` nor `error`, which
 * {@link outcomeOf} reads as "failed, no code".
 *
 * Not `fetched()`: that helper folds every failure into "unavailable", and these
 * pages need the code — `SOURCE_NOT_READY` is not an outage.
 */
export async function attempt<T>(
  call: () => Promise<T>,
): Promise<T | { data: undefined; error: undefined }> {
  try {
    return await call();
  } catch {
    return { data: undefined, error: undefined };
  }
}

/** The part of a result `cataloguePageState` decides from. */
export function outcomeOf(result: { data?: unknown; error?: unknown }): RequestOutcome {
  return { ok: result.data !== undefined, code: problemCode(result.error) };
}
